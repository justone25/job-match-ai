package com.jobmatch.summary;

import com.jobmatch.config.AppConfig;
import com.jobmatch.config.ConfigLoader;
import com.jobmatch.llm.LlmClient;
import com.jobmatch.llm.LlmRequest;
import com.jobmatch.llm.LlmResponse;
import com.jobmatch.llm.OllamaClient;
import com.jobmatch.model.monitor.BossJob;
import com.jobmatch.model.monitor.WeeklySummary;
import com.jobmatch.monitor.MonitorService;
import com.jobmatch.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating weekly JD summary reports.
 */
public class WeeklySummaryService {

    private static final Logger log = LoggerFactory.getLogger(WeeklySummaryService.class);
    private static final String PROMPT_TEMPLATE_PATH = "/prompts/weekly_summary.txt";

    private final StorageService storageService;
    private final MonitorService monitorService;
    private final LlmClient llmClient;
    private final String promptTemplate;

    public WeeklySummaryService() {
        this.storageService = StorageService.getInstance();
        this.monitorService = new MonitorService();

        // Initialize LLM client
        AppConfig config = ConfigLoader.load();
        this.llmClient = new OllamaClient(config.getLlm().getLocal());

        // Load prompt template
        this.promptTemplate = loadPromptTemplate();
    }

    /**
     * Generate weekly summary report.
     */
    public WeeklySummary generate() throws IOException {
        log.info("Generating weekly summary...");

        // Get this week's date range
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        // Get this week's jobs
        List<BossJob> weekJobs = monitorService.getThisWeekJobs();
        log.info("Found {} jobs this week", weekJobs.size());

        if (weekJobs.isEmpty()) {
            return WeeklySummary.builder()
                    .reportId(generateReportId(monday))
                    .weekStart(monday)
                    .weekEnd(sunday)
                    .totalJobs(0)
                    .newJobs(0)
                    .rawContent("本周暂无收集到新职位。")
                    .generatedAt(LocalDateTime.now())
                    .build();
        }

        // Build job data for prompt
        String jobData = formatJobsForPrompt(weekJobs);

        // Build prompt
        String prompt = promptTemplate
                .replace("{job_count}", String.valueOf(weekJobs.size()))
                .replace("{job_data}", jobData);

        // Call LLM
        log.info("Calling LLM for analysis...");
        LlmRequest request = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user(prompt)))
                .temperature(0.3)
                .build();

        LlmResponse response = llmClient.chat(request);
        String content = response.getContent();
        log.info("LLM analysis completed, {} characters", content.length());

        // Build summary
        WeeklySummary summary = WeeklySummary.builder()
                .reportId(generateReportId(monday))
                .weekStart(monday)
                .weekEnd(sunday)
                .totalJobs(weekJobs.size())
                .newJobs(weekJobs.size()) // All are new this week
                .rawContent(content)
                .generatedAt(LocalDateTime.now())
                .build();

        // Save to storage
        storageService.saveSummary(summary);
        log.info("Weekly summary saved: {}", summary.getReportId());

        return summary;
    }

    /**
     * Load summary by date string (yyyyMMdd).
     */
    public WeeklySummary loadByDate(String date) {
        String reportId = "weekly_" + date;
        return storageService.loadSummary(reportId).orElse(null);
    }

    /**
     * Load the latest summary.
     */
    public WeeklySummary loadLatest() {
        return storageService.loadLatestSummary().orElse(null);
    }

    /**
     * Format jobs for LLM prompt.
     */
    private String formatJobsForPrompt(List<BossJob> jobs) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < jobs.size(); i++) {
            BossJob job = jobs.get(i);
            sb.append("### 职位 ").append(i + 1).append("\n");
            sb.append("- **职位名称**: ").append(job.getTitle()).append("\n");
            sb.append("- **公司**: ").append(job.getCompany()).append("\n");
            sb.append("- **薪资**: ").append(job.getSalary()).append("\n");
            sb.append("- **地点**: ").append(job.getCity());
            if (job.getDistrict() != null && !job.getDistrict().isEmpty()) {
                sb.append(" · ").append(job.getDistrict());
            }
            sb.append("\n");
            sb.append("- **经验要求**: ").append(job.getExperience()).append("\n");
            sb.append("- **学历要求**: ").append(job.getEducation()).append("\n");

            if (job.getSkillTags() != null && !job.getSkillTags().isEmpty()) {
                sb.append("- **技能标签**: ").append(String.join(", ", job.getSkillTags())).append("\n");
            }

            if (job.getDescription() != null && !job.getDescription().isEmpty()) {
                // Truncate long descriptions
                String desc = job.getDescription();
                if (desc.length() > 500) {
                    desc = desc.substring(0, 500) + "...";
                }
                sb.append("- **职位描述**: ").append(desc).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate report ID.
     */
    private String generateReportId(LocalDate weekStart) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        return "weekly_" + weekStart.format(fmt);
    }

    /**
     * Load prompt template from resources.
     */
    private String loadPromptTemplate() {
        try (InputStream is = getClass().getResourceAsStream(PROMPT_TEMPLATE_PATH)) {
            if (is == null) {
                log.warn("Prompt template not found, using default");
                return getDefaultPromptTemplate();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.error("Failed to load prompt template", e);
            return getDefaultPromptTemplate();
        }
    }

    /**
     * Get default prompt template as fallback.
     */
    private String getDefaultPromptTemplate() {
        return """
                分析以下 {job_count} 个 AI 应用开发工程师职位，生成学习指导报告。

                ## 职位数据
                {job_data}

                ## 请输出：
                1. 技能需求排行（前10项技能）
                2. 市场趋势洞察
                3. 学习建议（3-5条）
                4. 值得关注的职位（3个）
                5. 总结
                """;
    }
}
