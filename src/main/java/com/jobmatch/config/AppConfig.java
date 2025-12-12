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
}
