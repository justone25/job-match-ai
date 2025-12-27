package com.jobmatch.crawler;

import com.jobmatch.model.monitor.BossJob;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crawler for BOSS jobs using Playwright.
 */
public class BossCrawler implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(BossCrawler.class);
    private static final String SEARCH_URL_TEMPLATE = "https://www.zhipin.com/web/geek/jobs?query=%s&city=%s";
    private static final Pattern JOB_ID_PATTERN = Pattern.compile("/job_detail/([^.]+)\\.html");

    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final BossLoginHandler loginHandler;
    private final boolean headless;

    // Filter settings
    private final int minSalaryK;
    private final boolean filterIntern;
    private final boolean onlyToday;

    public BossCrawler(boolean headless) {
        this(headless, 15, true, true);
    }

    public BossCrawler(boolean headless, int minSalaryK, boolean filterIntern, boolean onlyToday) {
        this.headless = headless;
        this.minSalaryK = minSalaryK;
        this.filterIntern = filterIntern;
        this.onlyToday = onlyToday;
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(headless ? 0 : 100)); // Slow down for non-headless debugging
        this.context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080));
        this.loginHandler = new BossLoginHandler();
        log.info("Crawler initialized with filters: minSalaryK={}, filterIntern={}, onlyToday={}", minSalaryK, filterIntern, onlyToday);
    }

    /**
     * Initialize crawler and ensure login.
     */
    public boolean initialize() {
        return loginHandler.ensureLogin(context);
    }

    /**
     * Crawl jobs for given search keywords.
     *
     * @param keywords search keywords
     * @param city     city code (101010100 for Beijing, 0 for all)
     * @param maxPages max pages to crawl
     * @return list of jobs found
     */
    public List<BossJob> crawlJobs(String keywords, String city, int maxPages) {
        List<BossJob> allJobs = new ArrayList<>();
        Page page = context.newPage();

        try {
            String encodedKeywords = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            String cityCode = mapCityToCode(city);
            String baseUrl = String.format(SEARCH_URL_TEMPLATE, encodedKeywords, cityCode);

            for (int pageNum = 1; pageNum <= maxPages; pageNum++) {
                String url = baseUrl + (pageNum > 1 ? "&page=" + pageNum : "");
                log.info("Crawling page {}: {}", pageNum, url);

                page.navigate(url);
                // Wait for page to load properly
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);

                // Initial wait for page rendering (longer to avoid skeleton screen)
                Thread.sleep(5000);

                // Wait for job links to load with retry
                boolean jobsLoaded = false;
                int retryCount = 0;
                while (!jobsLoaded && retryCount < 10) {
                    try {
                        // Check if job links are present using evaluate to avoid context issues
                        Object count = page.evaluate("() => document.querySelectorAll('a[href*=\"/job_detail/\"]').length");
                        int linkCount = count instanceof Number ? ((Number) count).intValue() : 0;
                        log.debug("Current job link count: {}", linkCount);
                        if (linkCount > 3) {  // Lower threshold
                            jobsLoaded = true;
                            log.info("Job links loaded on page {}: {} links found", pageNum, linkCount);
                        } else {
                            retryCount++;
                            log.debug("Waiting for more job links... (retry {}, found {})", retryCount, linkCount);
                            Thread.sleep(3000);  // Wait even longer between retries
                        }
                    } catch (Exception e) {
                        retryCount++;
                        log.debug("Wait interrupted: {}", e.getMessage());
                        Thread.sleep(3000);
                    }
                }

                if (!jobsLoaded) {
                    log.warn("No job links found on page {}, URL: {}", pageNum, page.url());
                    // Save debug screenshot
                    try {
                        java.nio.file.Path screenshotPath = java.nio.file.Paths.get(
                            System.getProperty("user.home"), ".jobmatch", "debug_screenshot.png");
                        page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath));
                        log.info("Debug screenshot saved to: {}", screenshotPath);
                    } catch (Exception se) {
                        log.warn("Failed to save debug screenshot: {}", se.getMessage());
                    }
                    break;
                }

                // Extract jobs from current page
                List<BossJob> pageJobs = extractJobsFromPage(page);
                log.info("Found {} jobs on page {}", pageJobs.size(), pageNum);
                allJobs.addAll(pageJobs);

                // Check if there's next page
                if (!hasNextPage(page)) {
                    log.info("No more pages after page {}", pageNum);
                    break;
                }

                // Random delay between pages
                Thread.sleep(2000 + (long) (Math.random() * 2000));
            }

        } catch (Exception e) {
            log.error("Crawl failed", e);
        } finally {
            page.close();
        }

        return allJobs;
    }

    /**
     * Extract job details from current page.
     */
    private List<BossJob> extractJobsFromPage(Page page) {
        List<BossJob> jobs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Find all job links with href containing '/job_detail/'
        List<ElementHandle> jobLinks = page.querySelectorAll("a[href*='/job_detail/']");
        if (jobLinks == null || jobLinks.isEmpty()) {
            log.warn("No job links found on page");
            return jobs;
        }

        log.info("Found {} raw job links on page", jobLinks.size());

        // Use a set to avoid duplicate jobs
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        int skippedEmpty = 0;
        int skippedDuplicate = 0;
        int filteredIntern = 0;
        int filteredSalary = 0;

        for (ElementHandle link : jobLinks) {
            try {
                String href = link.getAttribute("href");
                String jobId = extractJobId(href);
                if (jobId == null) {
                    log.debug("Skipped link with no job ID: {}", href);
                    continue;
                }
                if (seenIds.contains(jobId)) {
                    skippedDuplicate++;
                    continue;
                }
                seenIds.add(jobId);

                String url = href.startsWith("http") ? href : "https://www.zhipin.com" + href;
                String title = link.textContent().trim();
                if (title.isEmpty()) {
                    skippedEmpty++;
                    continue;
                }

                // Try to get parent listitem for more info
                ElementHandle listItem = link.evaluateHandle("el => el.closest('li')").asElement();

                String salary = "";
                String experience = "";
                String education = "";
                String company = "";
                String location = "";

                if (listItem != null) {
                    // Extract salary (text like "11-15K" or "11-15k")
                    String itemText = listItem.textContent();
                    java.util.regex.Matcher salaryMatcher = java.util.regex.Pattern
                            .compile("(\\d+-\\d+[Kk]|\\d+[Kk]以上|面议)", java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(itemText);
                    if (salaryMatcher.find()) {
                        salary = salaryMatcher.group(1);
                    }

                    // Extract company link
                    ElementHandle companyLink = listItem.querySelector("a[href*='/gongsi/']");
                    if (companyLink != null) {
                        company = companyLink.textContent().trim();
                    }

                    // Extract experience and education from nested list
                    List<ElementHandle> tags = listItem.querySelectorAll("li");
                    for (ElementHandle tag : tags) {
                        String tagText = tag.textContent().trim();
                        if (tagText.contains("年") || tagText.contains("经验") || tagText.contains("应届") || tagText.contains("在校")) {
                            experience = tagText;
                        } else if (tagText.contains("本科") || tagText.contains("硕士") || tagText.contains("博士") ||
                                tagText.contains("大专") || tagText.contains("学历")) {
                            education = tagText;
                        }
                    }

                    // Extract location (format: "城市·区·详细")
                    java.util.regex.Matcher locMatcher = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]+·[\\u4e00-\\u9fa5·]+)").matcher(itemText);
                    if (locMatcher.find()) {
                        location = locMatcher.group(1);
                    }
                }

                // === FILTER: Intern positions ===
                if (filterIntern) {
                    if (title.contains("实习") || title.toLowerCase().contains("intern") ||
                        experience.contains("在校") || experience.contains("实习") ||
                        salary.contains("元/天") || salary.contains("元/月")) {
                        filteredIntern++;
                        log.debug("Filtered out intern position: {}", title);
                        continue;
                    }
                }

                // === FILTER: Below market salary ===
                if (minSalaryK > 0 && !salary.isEmpty() && !salary.equals("面议")) {
                    int minSalary = parseSalaryMin(salary);
                    if (minSalary > 0 && minSalary < minSalaryK) {
                        filteredSalary++;
                        log.debug("Filtered out low salary job: {} ({})", title, salary);
                        continue;
                    }
                }

                String[] locParts = location.split("·");
                String city = locParts.length > 0 ? locParts[0] : "";
                String district = locParts.length > 1 ? locParts[1] : "";

                BossJob job = BossJob.builder()
                        .jobId(jobId)
                        .title(title)
                        .salary(salary)
                        .company(company)
                        .city(city)
                        .district(district)
                        .experience(experience)
                        .education(education)
                        .url(url)
                        .firstSeenAt(now)
                        .lastUpdatedAt(now)
                        .status("ACTIVE")
                        .build();

                jobs.add(job);
                log.debug("Extracted job: {} @ {} ({})", title, company, salary);

            } catch (Exception e) {
                log.debug("Failed to extract job from link: {}", e.getMessage());
            }
        }

        log.info("Extraction stats: {} unique IDs, {} empty titles, {} duplicates, {} intern filtered, {} salary filtered, {} passed",
                seenIds.size(), skippedEmpty, skippedDuplicate, filteredIntern, filteredSalary, jobs.size());
        return jobs;
    }

    /**
     * Extract job info from a single job card.
     */
    private BossJob extractJobFromCard(ElementHandle card, LocalDateTime now) {
        // Get job link and extract ID
        ElementHandle linkEl = card.querySelector(".job-card-left a");
        if (linkEl == null) return null;

        String href = linkEl.getAttribute("href");
        String jobId = extractJobId(href);
        if (jobId == null) return null;

        String url = "https://www.zhipin.com" + href;

        // Extract title
        ElementHandle titleEl = card.querySelector(".job-name");
        String title = titleEl != null ? titleEl.textContent().trim() : "";

        // Extract salary
        ElementHandle salaryEl = card.querySelector(".salary");
        String salary = salaryEl != null ? salaryEl.textContent().trim() : "";

        // Extract company
        ElementHandle companyEl = card.querySelector(".company-name a");
        String company = companyEl != null ? companyEl.textContent().trim() : "";

        // Extract location
        ElementHandle areaEl = card.querySelector(".job-area");
        String area = areaEl != null ? areaEl.textContent().trim() : "";
        String[] areaParts = area.split("·");
        String city = areaParts.length > 0 ? areaParts[0].trim() : "";
        String district = areaParts.length > 1 ? areaParts[1].trim() : "";

        // Extract tags (experience, education)
        List<ElementHandle> tagEls = card.querySelectorAll(".tag-list li");
        String experience = "";
        String education = "";
        for (ElementHandle tagEl : tagEls) {
            String text = tagEl.textContent().trim();
            if (text.contains("年") || text.contains("经验")) {
                experience = text;
            } else if (text.contains("本科") || text.contains("硕士") || text.contains("博士") ||
                    text.contains("大专") || text.contains("学历")) {
                education = text;
            }
        }

        // Extract skill tags
        List<String> skillTags = new ArrayList<>();
        List<ElementHandle> skillEls = card.querySelectorAll(".job-card-footer .tag-list li");
        for (ElementHandle skillEl : skillEls) {
            String skill = skillEl.textContent().trim();
            if (!skill.isEmpty()) {
                skillTags.add(skill);
            }
        }

        // Extract company info
        ElementHandle companySizeEl = card.querySelector(".company-tag-list li");
        String companySize = companySizeEl != null ? companySizeEl.textContent().trim() : "";

        ElementHandle industryEl = card.querySelector(".company-tag-list li:nth-child(2)");
        String industry = industryEl != null ? industryEl.textContent().trim() : "";

        return BossJob.builder()
                .jobId(jobId)
                .title(title)
                .salary(salary)
                .company(company)
                .companySize(companySize)
                .industry(industry)
                .city(city)
                .district(district)
                .experience(experience)
                .education(education)
                .skillTags(skillTags)
                .url(url)
                .firstSeenAt(now)
                .lastUpdatedAt(now)
                .status("ACTIVE")
                .build();
    }

    /**
     * Extract job ID from URL.
     */
    private String extractJobId(String href) {
        if (href == null) return null;
        Matcher matcher = JOB_ID_PATTERN.matcher(href);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Parse minimum salary from salary string like "15-25K" or "20K以上".
     * @return minimum salary in K, or 0 if cannot parse
     */
    private int parseSalaryMin(String salary) {
        if (salary == null || salary.isEmpty()) return 0;
        try {
            // Pattern: "15-25K" or "20-30K·13薪" (case insensitive)
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d+)[-~](\\d+)[Kk]", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(salary);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            // Pattern: "20K以上" (case insensitive)
            matcher = java.util.regex.Pattern
                    .compile("(\\d+)[Kk]以上", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(salary);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.debug("Failed to parse salary: {}", salary);
        }
        return 0;
    }

    /**
     * Check if there's a next page.
     */
    private boolean hasNextPage(Page page) {
        ElementHandle nextBtn = page.querySelector(".ui-icon-arrow-right");
        if (nextBtn == null) return false;

        ElementHandle parent = nextBtn.evaluateHandle("el => el.parentElement").asElement();
        if (parent == null) return false;

        String className = parent.getAttribute("class");
        return className != null && !className.contains("disabled");
    }

    /**
     * Map city name to BOSS city code.
     */
    private String mapCityToCode(String city) {
        if (city == null || city.isEmpty() || "全国".equals(city)) {
            return "100010000"; // All cities
        }

        // Common city codes
        return switch (city) {
            case "北京" -> "101010100";
            case "上海" -> "101020100";
            case "广州" -> "101280100";
            case "深圳" -> "101280600";
            case "杭州" -> "101210100";
            case "成都" -> "101270100";
            case "南京" -> "101190100";
            case "武汉" -> "101200100";
            case "西安" -> "101110100";
            case "苏州" -> "101190400";
            default -> "100010000"; // Default to all
        };
    }

    /**
     * Get job details by visiting job page.
     */
    public BossJob enrichJobDetails(BossJob job) {
        Page page = context.newPage();
        try {
            page.navigate(job.getUrl());
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);

            // Wait for job description to load
            try {
                page.waitForSelector(".job-sec-text", new Page.WaitForSelectorOptions().setTimeout(10000));
            } catch (Exception e) {
                log.debug("Job description selector timeout, trying to extract anyway");
            }

            // Extract salary from detail page (not encrypted like list page)
            ElementHandle salaryEl = page.querySelector(".salary");
            if (salaryEl != null) {
                String salary = salaryEl.textContent().trim();
                job.setSalary(salary);
                log.debug("Extracted salary from detail page: {}", salary);
            }

            // Extract full job description
            ElementHandle descEl = page.querySelector(".job-detail-section .job-sec-text");
            if (descEl != null) {
                job.setDescription(descEl.textContent().trim());
            }

            // Extract HR active status to determine if job is recently updated
            String pageText = page.textContent("body");
            if (pageText != null) {
                if (pageText.contains("今日活跃") || pageText.contains("刚刚活跃")) {
                    // Job is active today
                    job.setPublishedAt(LocalDateTime.now());
                    log.debug("Job {} is active today", job.getJobId());
                } else if (pageText.contains("本周活跃") || pageText.contains("3日内活跃")) {
                    // Job was active within this week
                    job.setPublishedAt(LocalDateTime.now().minusDays(3));
                } else {
                    // Job might be older
                    job.setPublishedAt(LocalDateTime.now().minusDays(7));
                }
            }

            // Random delay
            Thread.sleep(1000 + (long) (Math.random() * 1000));

        } catch (Exception e) {
            log.warn("Failed to enrich job details for {}: {}", job.getJobId(), e.getMessage());
        } finally {
            page.close();
        }

        return job;
    }

    @Override
    public void close() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
