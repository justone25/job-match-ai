package com.jobmatch.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobmatch.config.AppConfig;
import com.jobmatch.config.ConfigLoader;
import com.jobmatch.model.match.MatchReport;
import com.jobmatch.model.monitor.BossJob;
import com.jobmatch.model.monitor.WeeklySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Service for storing and retrieving match reports and cache data.
 * Uses local JSON files for persistence.
 */
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault());

    private static StorageService instance;

    private final ObjectMapper objectMapper;
    private final Path dataDir;
    private final Path cacheDir;
    private final Path historyDir;
    private final Path feedbackDir;
    private final Path monitorDir;
    private final Path summaryDir;
    private final boolean cacheEnabled;
    private final int cacheTtlDays;

    private StorageService(AppConfig.StorageConfig config) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.dataDir = Paths.get(ConfigLoader.expandPath(config.getDataDir()));
        this.cacheDir = Paths.get(ConfigLoader.expandPath(config.getCacheDir()));
        this.historyDir = dataDir.resolve("history");
        this.feedbackDir = dataDir.resolve("feedback");
        this.monitorDir = dataDir.resolve("monitor");
        this.summaryDir = dataDir.resolve("summary");
        this.cacheEnabled = config.isCacheEnabled();
        this.cacheTtlDays = config.getCacheTtlDays();

        initDirectories();
    }

    public static synchronized StorageService getInstance() {
        if (instance == null) {
            AppConfig config = ConfigLoader.load();
            instance = new StorageService(config.getStorage());
        }
        return instance;
    }

    private void initDirectories() {
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(cacheDir);
            Files.createDirectories(historyDir);
            Files.createDirectories(feedbackDir);
            Files.createDirectories(monitorDir);
            Files.createDirectories(summaryDir);
            log.debug("Storage directories initialized: data={}, cache={}", dataDir, cacheDir);
        } catch (IOException e) {
            log.error("Failed to create storage directories", e);
        }
    }

    // ===================== History Management =====================

    /**
     * Save a match report to history.
     */
    public String saveReport(MatchReport report) throws IOException {
        String id = generateReportId();
        String filename = id + ".json";
        Path filePath = historyDir.resolve(filename);

        objectMapper.writeValue(filePath.toFile(), report);
        log.info("Saved report to history: {}", filename);

        return id;
    }

    /**
     * Load a match report by ID.
     */
    public Optional<MatchReport> loadReport(String id) {
        Path filePath = historyDir.resolve(id + ".json");
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        try {
            MatchReport report = objectMapper.readValue(filePath.toFile(), MatchReport.class);
            return Optional.of(report);
        } catch (IOException e) {
            log.error("Failed to load report: {}", id, e);
            return Optional.empty();
        }
    }

    /**
     * List all saved reports.
     */
    public List<HistoryEntry> listHistory() {
        List<HistoryEntry> entries = new ArrayList<>();

        try (Stream<Path> files = Files.list(historyDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            MatchReport report = objectMapper.readValue(p.toFile(), MatchReport.class);
                            String id = p.getFileName().toString().replace(".json", "");
                            entries.add(HistoryEntry.builder()
                                    .id(id)
                                    .timestamp(report.getMeta() != null ? report.getMeta().getParseTime() : null)
                                    .matchLevel(report.getSummary() != null ? report.getSummary().getMatchLevel() : null)
                                    .score(report.getSummary() != null ? report.getSummary().getOverallScore() : 0)
                                    .oneLine(report.getSummary() != null ? report.getSummary().getOneLine() : null)
                                    .build());
                        } catch (IOException e) {
                            log.warn("Failed to read history entry: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list history", e);
        }

        return entries;
    }

    /**
     * Delete a report from history.
     */
    public boolean deleteReport(String id) {
        Path filePath = historyDir.resolve(id + ".json");
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Failed to delete report: {}", id, e);
            return false;
        }
    }

    /**
     * Clear all history.
     */
    public int clearHistory() {
        int count = 0;
        try (Stream<Path> files = Files.list(historyDir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    Files.delete(p);
                    count++;
                } catch (IOException e) {
                    log.warn("Failed to delete: {}", p, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to clear history", e);
        }
        return count;
    }

    // ===================== Cache Management =====================

    /**
     * Get cached parse result by content hash.
     */
    public <T> Optional<T> getFromCache(String hash, Class<T> type) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        Path cachePath = cacheDir.resolve(hash + ".json");
        if (!Files.exists(cachePath)) {
            return Optional.empty();
        }

        // Check TTL
        try {
            if (isExpired(cachePath)) {
                Files.delete(cachePath);
                return Optional.empty();
            }

            T result = objectMapper.readValue(cachePath.toFile(), type);
            log.debug("Cache hit: {}", hash.substring(0, 8));
            return Optional.of(result);
        } catch (IOException e) {
            log.warn("Failed to read cache: {}", hash, e);
            return Optional.empty();
        }
    }

    /**
     * Save to cache.
     */
    public void saveToCache(String hash, Object data) {
        if (!cacheEnabled) {
            return;
        }

        Path cachePath = cacheDir.resolve(hash + ".json");
        try {
            objectMapper.writeValue(cachePath.toFile(), data);
            log.debug("Saved to cache: {}", hash.substring(0, 8));
        } catch (IOException e) {
            log.warn("Failed to save to cache: {}", hash, e);
        }
    }

    /**
     * Clear expired cache entries.
     */
    public int clearExpiredCache() {
        int count = 0;
        try (Stream<Path> files = Files.list(cacheDir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    if (isExpired(p)) {
                        Files.delete(p);
                        count++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to check/delete: {}", p, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to clear expired cache", e);
        }
        return count;
    }

    /**
     * Clear all cache.
     */
    public int clearAllCache() {
        int count = 0;
        try (Stream<Path> files = Files.list(cacheDir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    Files.delete(p);
                    count++;
                } catch (IOException e) {
                    log.warn("Failed to delete: {}", p, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to clear cache", e);
        }
        return count;
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getCacheStats() {
        long totalSize = 0;
        int fileCount = 0;
        int expiredCount = 0;

        try (Stream<Path> files = Files.list(cacheDir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try {
                    fileCount++;
                    totalSize += Files.size(p);
                    if (isExpired(p)) {
                        expiredCount++;
                    }
                } catch (IOException e) {
                    log.warn("Failed to get size: {}", p, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to get cache stats", e);
        }

        return CacheStats.builder()
                .fileCount(fileCount)
                .totalSizeBytes(totalSize)
                .expiredCount(expiredCount)
                .cacheDir(cacheDir.toString())
                .enabled(cacheEnabled)
                .ttlDays(cacheTtlDays)
                .build();
    }

    // ===================== Feedback Management =====================

    /**
     * Save user feedback.
     */
    public void saveFeedback(Feedback feedback) throws IOException {
        String filename = FILE_DATE_FORMAT.format(Instant.now()) + ".json";
        Path filePath = feedbackDir.resolve(filename);
        objectMapper.writeValue(filePath.toFile(), feedback);
        log.info("Saved feedback: {}", filename);
    }

    /**
     * List all feedback entries.
     */
    public List<Feedback> listFeedback() {
        List<Feedback> feedbacks = new ArrayList<>();
        try (Stream<Path> files = Files.list(feedbackDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            feedbacks.add(objectMapper.readValue(p.toFile(), Feedback.class));
                        } catch (IOException e) {
                            log.warn("Failed to read feedback: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list feedback", e);
        }
        return feedbacks;
    }

    /**
     * Get feedback statistics.
     */
    public FeedbackStats getFeedbackStats() {
        List<Feedback> feedbacks = listFeedback();

        int total = feedbacks.size();
        int helpful = (int) feedbacks.stream().filter(f -> f.getRating() == 1).count();
        int neutral = (int) feedbacks.stream().filter(f -> f.getRating() == 2).count();
        int notHelpful = (int) feedbacks.stream().filter(f -> f.getRating() == 3).count();

        return FeedbackStats.builder()
                .total(total)
                .helpful(helpful)
                .neutral(neutral)
                .notHelpful(notHelpful)
                .build();
    }

    // ===================== Helpers =====================

    private String generateReportId() {
        return FILE_DATE_FORMAT.format(Instant.now());
    }

    private boolean isExpired(Path path) throws IOException {
        Instant lastModified = Files.getLastModifiedTime(path).toInstant();
        Instant expireTime = Instant.now().minusSeconds(cacheTtlDays * 24L * 60 * 60);
        return lastModified.isBefore(expireTime);
    }

    // ===================== Data Classes =====================

    @lombok.Data
    @lombok.Builder
    public static class HistoryEntry {
        private String id;
        private Instant timestamp;
        private String matchLevel;
        private int score;
        private String oneLine;
    }

    @lombok.Data
    @lombok.Builder
    public static class CacheStats {
        private int fileCount;
        private long totalSizeBytes;
        private int expiredCount;
        private String cacheDir;
        private boolean enabled;
        private int ttlDays;

        public String getTotalSizeMb() {
            return String.format("%.2f", totalSizeBytes / (1024.0 * 1024.0));
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Feedback {
        private String analysisId;
        private int rating;  // 1=helpful, 2=neutral, 3=not helpful
        private String comment;
        private Instant timestamp;
    }

    @lombok.Data
    @lombok.Builder
    public static class FeedbackStats {
        private int total;
        private int helpful;
        private int neutral;
        private int notHelpful;

        public double getHelpfulRate() {
            return total > 0 ? (double) helpful / total * 100 : 0;
        }
    }

    // ===================== Monitor Job Management =====================

    /**
     * Save a BOSS job.
     */
    public void saveJob(BossJob job) throws IOException {
        String filename = job.getJobId() + ".json";
        Path filePath = monitorDir.resolve(filename);
        objectMapper.writeValue(filePath.toFile(), job);
        log.debug("Saved job: {}", job.getJobId());
    }

    /**
     * Load a BOSS job by ID.
     */
    public Optional<BossJob> loadJob(String jobId) {
        Path filePath = monitorDir.resolve(jobId + ".json");
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(filePath.toFile(), BossJob.class));
        } catch (IOException e) {
            log.error("Failed to load job: {}", jobId, e);
            return Optional.empty();
        }
    }

    /**
     * Check if a job exists.
     */
    public boolean jobExists(String jobId) {
        return Files.exists(monitorDir.resolve(jobId + ".json"));
    }

    /**
     * List all saved jobs.
     */
    public List<BossJob> listJobs() {
        List<BossJob> jobs = new ArrayList<>();
        try (Stream<Path> files = Files.list(monitorDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            jobs.add(objectMapper.readValue(p.toFile(), BossJob.class));
                        } catch (IOException e) {
                            log.warn("Failed to read job: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list jobs", e);
        }
        return jobs;
    }

    /**
     * Get all job IDs.
     */
    public Set<String> getAllJobIds() {
        Set<String> ids = new HashSet<>();
        try (Stream<Path> files = Files.list(monitorDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String filename = p.getFileName().toString();
                        ids.add(filename.replace(".json", ""));
                    });
        } catch (IOException e) {
            log.error("Failed to list job IDs", e);
        }
        return ids;
    }

    /**
     * Get monitor directory path.
     */
    public Path getMonitorDir() {
        return monitorDir;
    }

    // ===================== Weekly Summary Management =====================

    /**
     * Save a weekly summary.
     */
    public void saveSummary(WeeklySummary summary) throws IOException {
        String filename = summary.getReportId() + ".json";
        Path filePath = summaryDir.resolve(filename);
        objectMapper.writeValue(filePath.toFile(), summary);
        log.info("Saved weekly summary: {}", summary.getReportId());
    }

    /**
     * Load a weekly summary by ID.
     */
    public Optional<WeeklySummary> loadSummary(String reportId) {
        Path filePath = summaryDir.resolve(reportId + ".json");
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(filePath.toFile(), WeeklySummary.class));
        } catch (IOException e) {
            log.error("Failed to load summary: {}", reportId, e);
            return Optional.empty();
        }
    }

    /**
     * Load the latest weekly summary.
     */
    public Optional<WeeklySummary> loadLatestSummary() {
        try (Stream<Path> files = Files.list(summaryDir)) {
            Optional<Path> latest = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .max(Comparator.comparing(p -> p.getFileName().toString()));

            if (latest.isPresent()) {
                return Optional.of(objectMapper.readValue(latest.get().toFile(), WeeklySummary.class));
            }
        } catch (IOException e) {
            log.error("Failed to load latest summary", e);
        }
        return Optional.empty();
    }

    /**
     * List all weekly summaries.
     */
    public List<WeeklySummary> listSummaries() {
        List<WeeklySummary> summaries = new ArrayList<>();
        try (Stream<Path> files = Files.list(summaryDir)) {
            files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            summaries.add(objectMapper.readValue(p.toFile(), WeeklySummary.class));
                        } catch (IOException e) {
                            log.warn("Failed to read summary: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to list summaries", e);
        }
        return summaries;
    }

    /**
     * Get summary directory path.
     */
    public Path getSummaryDir() {
        return summaryDir;
    }
}
