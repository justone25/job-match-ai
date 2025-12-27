package com.jobmatch.config;

import lombok.Data;

/**
 * Main application configuration.
 * Maps to the YAML configuration file structure.
 */
@Data
public class AppConfig {

    private LlmConfig llm = new LlmConfig();
    private StorageConfig storage = new StorageConfig();
    private LoggingConfig logging = new LoggingConfig();
    private OutputConfig output = new OutputConfig();
    private PrivacyConfig privacy = new PrivacyConfig();
    private MonitorConfig monitor = new MonitorConfig();
    private WeeklySummaryConfig weeklySummary = new WeeklySummaryConfig();

    @Data
    public static class LlmConfig {
        private String provider = "local";
        private LocalLlmConfig local = new LocalLlmConfig();
        private CloudLlmConfig cloud = new CloudLlmConfig();
        private CommonLlmConfig common = new CommonLlmConfig();
    }

    @Data
    public static class LocalLlmConfig {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:14b";
        private int timeout = 120;
    }

    @Data
    public static class CloudLlmConfig {
        private String apiKey = "${LLM_API_KEY}";
        private String model = "gpt-4o-mini";
        private String baseUrl = "https://api.openai.com/v1";
        private int timeout = 60;
    }

    @Data
    public static class CommonLlmConfig {
        private double temperature = 0.1;
        private int maxTokens = 4096;
        private int retryTimes = 2;
    }

    @Data
    public static class StorageConfig {
        private String dataDir = "~/.jobmatch/data";
        private String cacheDir = "~/.jobmatch/cache";
        private boolean cacheEnabled = true;
        private int cacheMaxSizeMb = 100;
        private int cacheTtlDays = 7;
    }

    @Data
    public static class LoggingConfig {
        private String level = "INFO";
        private String file = "~/.jobmatch/logs/jobmatch.log";
        private int maxSizeMb = 10;
        private int maxFiles = 5;
        private boolean console = true;
    }

    @Data
    public static class OutputConfig {
        private String defaultFormat = "markdown";
        private String language = "zh";
        private boolean color = true;
    }

    @Data
    public static class PrivacyConfig {
        private boolean piiMasking = true;
        private boolean telemetry = false;
    }

    @Data
    public static class MonitorConfig {
        private boolean enabled = true;
        private String searchKeywords = "AI应用开发";
        private String city = "全国";
        private String salaryRange = "";
        private String experience = "";
        private java.util.List<String> pollingTimes = java.util.List.of("10:00", "18:00");
        private int retentionDays = 30;
        private boolean headless = true;
        // Filter settings
        private int minSalaryK = 15;        // Minimum salary in K
        private boolean filterIntern = true; // Filter out intern positions
        private boolean onlyToday = true;    // Only fetch jobs posted today
    }

    @Data
    public static class WeeklySummaryConfig {
        private boolean enabled = true;
        private String dayOfWeek = "MONDAY";
        private int hour = 9;
        private java.util.List<String> focusAreas = java.util.List.of(
                "大模型应用", "RAG", "Agent", "LangChain", "向量数据库");
    }
}
