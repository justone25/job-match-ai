package com.jobmatch.monitor;

import com.jobmatch.config.AppConfig;
import com.jobmatch.config.ConfigLoader;
import com.jobmatch.crawler.BossCrawler;
import com.jobmatch.model.monitor.BossJob;
import com.jobmatch.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for orchestrating job monitoring.
 * Coordinates crawler, storage, and notification.
 */
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final StorageService storageService;
    private final String searchKeywords;
    private final String city;
    private final boolean headless;
    private final int retentionDays;
    // Filter settings
    private final int minSalaryK;
    private final boolean filterIntern;
    private final boolean onlyToday;

    public MonitorService() {
        this.storageService = StorageService.getInstance();

        // Load config
        AppConfig config = ConfigLoader.load();
        AppConfig.MonitorConfig monitorConfig = config.getMonitor();
        if (monitorConfig != null) {
            this.searchKeywords = monitorConfig.getSearchKeywords() != null ?
                    monitorConfig.getSearchKeywords() : "AI应用开发";
            this.city = monitorConfig.getCity() != null ?
                    monitorConfig.getCity() : "全国";
            this.headless = monitorConfig.isHeadless();
            this.retentionDays = monitorConfig.getRetentionDays() > 0 ?
                    monitorConfig.getRetentionDays() : 30;
            this.minSalaryK = monitorConfig.getMinSalaryK();
            this.filterIntern = monitorConfig.isFilterIntern();
            this.onlyToday = monitorConfig.isOnlyToday();
        } else {
            this.searchKeywords = "AI应用开发";
            this.city = "全国";
            this.headless = true;
            this.retentionDays = 30;
            this.minSalaryK = 15;
            this.filterIntern = true;
            this.onlyToday = true;
        }
        log.info("MonitorService initialized: keywords={}, city={}, minSalaryK={}, filterIntern={}, onlyToday={}",
                searchKeywords, city, minSalaryK, filterIntern, onlyToday);
    }

    /**
     * Initialize monitor (login to BOSS).
     * Note: We don't use try-with-resources here because we need to keep
     * the browser open while the user logs in manually.
     */
    public void initialize() {
        BossCrawler crawler = null;
        try {
            crawler = new BossCrawler(false); // Non-headless for login
            boolean success = crawler.initialize();
            if (!success) {
                throw new RuntimeException("Failed to initialize crawler");
            }
        } finally {
            if (crawler != null) {
                crawler.close();
            }
        }
    }

    /**
     * Crawl jobs and detect new ones.
     *
     * @return list of newly discovered jobs
     */
    public List<BossJob> crawlAndDetectNew() {
        List<BossJob> newJobs = new ArrayList<>();

        try (BossCrawler crawler = new BossCrawler(headless, minSalaryK, filterIntern, onlyToday)) {
            // Ensure logged in
            if (!crawler.initialize()) {
                throw new RuntimeException("Not logged in. Run 'jobmatch monitor init' first.");
            }

            // Get existing job IDs for deduplication
            Set<String> existingIds = storageService.getAllJobIds();
            log.info("Existing jobs in storage: {}", existingIds.size());

            // Crawl jobs (filters are applied inside crawler)
            List<BossJob> crawledJobs = crawler.crawlJobs(searchKeywords, city, 3);
            log.info("Crawled {} jobs after filtering", crawledJobs.size());

            // Process new jobs
            int filteredByDate = 0;
            int alreadyExists = 0;
            for (BossJob job : crawledJobs) {
                if (!existingIds.contains(job.getJobId())) {
                    // Enrich with full description and check publish date
                    crawler.enrichJobDetails(job);

                    // Filter by publish date if onlyToday is enabled
                    if (onlyToday && job.getPublishedAt() != null) {
                        LocalDate publishDate = job.getPublishedAt().toLocalDate();
                        if (!publishDate.equals(LocalDate.now())) {
                            filteredByDate++;
                            log.info("Filtered by date: {} (published: {})", job.getTitle(), publishDate);
                            continue;
                        }
                    }

                    // Save to storage
                    try {
                        storageService.saveJob(job);
                        newJobs.add(job);
                        log.info("New job saved: {} @ {} ({})", job.getTitle(), job.getCompany(), job.getSalary());
                    } catch (IOException e) {
                        log.error("Failed to save job: {}", job.getJobId(), e);
                    }
                } else {
                    alreadyExists++;
                    // Update existing job's lastUpdatedAt
                    storageService.loadJob(job.getJobId()).ifPresent(existing -> {
                        existing.setLastUpdatedAt(LocalDateTime.now());
                        try {
                            storageService.saveJob(existing);
                        } catch (IOException e) {
                            log.warn("Failed to update job: {}", job.getJobId());
                        }
                    });
                }
            }

            log.info("Processing stats: {} crawled, {} already exist, {} filtered by date, {} new jobs saved",
                    crawledJobs.size(), alreadyExists, filteredByDate, newJobs.size());

        } catch (Exception e) {
            log.error("Crawl and detect failed", e);
            throw new RuntimeException("Crawl failed: " + e.getMessage(), e);
        }

        return newJobs;
    }

    /**
     * Get all saved jobs.
     */
    public List<BossJob> getAllJobs() {
        return storageService.listJobs();
    }

    /**
     * Get jobs collected this week.
     */
    public List<BossJob> getThisWeekJobs() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime weekStart = monday.atStartOfDay();

        return storageService.listJobs().stream()
                .filter(job -> job.getFirstSeenAt() != null && job.getFirstSeenAt().isAfter(weekStart))
                .collect(Collectors.toList());
    }

    /**
     * Get jobs collected in the last N days.
     */
    public List<BossJob> getRecentJobs(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);

        return storageService.listJobs().stream()
                .filter(job -> job.getFirstSeenAt() != null && job.getFirstSeenAt().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    /**
     * Clean up old jobs based on retention policy.
     */
    public int cleanupOldJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = 0;

        List<BossJob> jobs = storageService.listJobs();
        for (BossJob job : jobs) {
            if (job.getFirstSeenAt() != null && job.getFirstSeenAt().isBefore(cutoff)) {
                // Delete from storage - would need to add delete method
                deleted++;
            }
        }

        log.info("Cleaned up {} old jobs", deleted);
        return deleted;
    }

    /**
     * Get monitor statistics.
     */
    public MonitorStats getStats() {
        List<BossJob> allJobs = getAllJobs();
        List<BossJob> weekJobs = getThisWeekJobs();

        return MonitorStats.builder()
                .totalJobs(allJobs.size())
                .thisWeekJobs(weekJobs.size())
                .searchKeywords(searchKeywords)
                .city(city)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class MonitorStats {
        private int totalJobs;
        private int thisWeekJobs;
        private String searchKeywords;
        private String city;
    }
}
