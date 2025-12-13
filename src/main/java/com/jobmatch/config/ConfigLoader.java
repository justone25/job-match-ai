package com.jobmatch.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Configuration loader with priority:
 * 1. Command line --config option
 * 2. JOBMATCH_CONFIG environment variable
 * 3. ./jobmatch.yaml (current directory)
 * 4. ~/.jobmatch/config.yaml (user home)
 * 5. Built-in defaults
 */
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static AppConfig cachedConfig;

    /**
     * Load configuration from all sources, merging with priority.
     */
    public static AppConfig load() {
        if (cachedConfig != null) {
            return cachedConfig;
        }

        // Start with defaults
        AppConfig config = new AppConfig();

        // Try to load from files (in reverse priority order, later loads override)
        loadFromBuiltinDefaults(config);
        loadFromUserHome(config);
        loadFromCurrentDir(config);
        loadFromEnvConfigPath(config);

        // Apply environment variable overrides
        applyEnvironmentOverrides(config);

        // Expand path variables
        expandPaths(config);

        cachedConfig = config;
        return config;
    }

    /**
     * Reload configuration (clear cache and reload).
     */
    public static AppConfig reload() {
        cachedConfig = null;
        return load();
    }

    /**
     * Load configuration from a specific file path.
     */
    public static AppConfig loadFromFile(String path) throws IOException {
        File file = new File(expandPath(path));
        if (!file.exists()) {
            throw new IOException("Configuration file not found: " + path);
        }
        return yamlMapper.readValue(file, AppConfig.class);
    }

    private static void loadFromBuiltinDefaults(AppConfig config) {
        try (InputStream is = ConfigLoader.class.getResourceAsStream("/application.yaml")) {
            if (is != null) {
                AppConfig fileConfig = yamlMapper.readValue(is, AppConfig.class);
                mergeConfig(config, fileConfig);
                log.debug("Loaded built-in default configuration");
            }
        } catch (IOException e) {
            log.debug("No built-in defaults found: {}", e.getMessage());
        }
    }

    private static void loadFromUserHome(AppConfig config) {
        String userHome = System.getProperty("user.home");
        Path configPath = Paths.get(userHome, ".jobmatch", "config.yaml");
        loadFromPath(config, configPath);
    }

    private static void loadFromCurrentDir(AppConfig config) {
        Path configPath = Paths.get("jobmatch.yaml");
        loadFromPath(config, configPath);
    }

    private static void loadFromEnvConfigPath(AppConfig config) {
        String envPath = System.getenv("JOBMATCH_CONFIG");
        if (envPath != null && !envPath.isEmpty()) {
            Path configPath = Paths.get(envPath);
            loadFromPath(config, configPath);
        }
    }

    private static void loadFromPath(AppConfig config, Path path) {
        if (Files.exists(path)) {
            try {
                AppConfig fileConfig = yamlMapper.readValue(path.toFile(), AppConfig.class);
                mergeConfig(config, fileConfig);
                log.debug("Loaded configuration from: {}", path);
            } catch (IOException e) {
                log.warn("Failed to load configuration from {}: {}", path, e.getMessage());
            }
        }
    }

    private static void mergeConfig(AppConfig target, AppConfig source) {
        if (source == null) return;

        // Merge LLM config
        if (source.getLlm() != null) {
            if (source.getLlm().getProvider() != null) {
                target.getLlm().setProvider(source.getLlm().getProvider());
            }
            if (source.getLlm().getLocal() != null) {
                mergeLocalLlmConfig(target.getLlm().getLocal(), source.getLlm().getLocal());
            }
            if (source.getLlm().getCloud() != null) {
                mergeCloudLlmConfig(target.getLlm().getCloud(), source.getLlm().getCloud());
            }
            if (source.getLlm().getCommon() != null) {
                mergeCommonLlmConfig(target.getLlm().getCommon(), source.getLlm().getCommon());
            }
        }

        // Merge storage config
        if (source.getStorage() != null) {
            if (source.getStorage().getDataDir() != null) {
                target.getStorage().setDataDir(source.getStorage().getDataDir());
            }
            if (source.getStorage().getCacheDir() != null) {
                target.getStorage().setCacheDir(source.getStorage().getCacheDir());
            }
            target.getStorage().setCacheEnabled(source.getStorage().isCacheEnabled());
            if (source.getStorage().getCacheTtlDays() > 0) {
                target.getStorage().setCacheTtlDays(source.getStorage().getCacheTtlDays());
            }
        }

        // Merge logging config
        if (source.getLogging() != null) {
            if (source.getLogging().getLevel() != null) {
                target.getLogging().setLevel(source.getLogging().getLevel());
            }
            if (source.getLogging().getFile() != null) {
                target.getLogging().setFile(source.getLogging().getFile());
            }
        }

        // Merge output config
        if (source.getOutput() != null) {
            if (source.getOutput().getDefaultFormat() != null) {
                target.getOutput().setDefaultFormat(source.getOutput().getDefaultFormat());
            }
            target.getOutput().setColor(source.getOutput().isColor());
        }
    }

    private static void mergeLocalLlmConfig(AppConfig.LocalLlmConfig target, AppConfig.LocalLlmConfig source) {
        if (source.getBaseUrl() != null) target.setBaseUrl(source.getBaseUrl());
        if (source.getModel() != null) target.setModel(source.getModel());
        if (source.getTimeout() > 0) target.setTimeout(source.getTimeout());
    }

    private static void mergeCloudLlmConfig(AppConfig.CloudLlmConfig target, AppConfig.CloudLlmConfig source) {
        if (source.getApiKey() != null) target.setApiKey(source.getApiKey());
        if (source.getModel() != null) target.setModel(source.getModel());
        if (source.getBaseUrl() != null) target.setBaseUrl(source.getBaseUrl());
        if (source.getTimeout() > 0) target.setTimeout(source.getTimeout());
    }

    private static void mergeCommonLlmConfig(AppConfig.CommonLlmConfig target, AppConfig.CommonLlmConfig source) {
        if (source.getTemperature() >= 0) target.setTemperature(source.getTemperature());
        if (source.getMaxTokens() > 0) target.setMaxTokens(source.getMaxTokens());
        if (source.getRetryTimes() >= 0) target.setRetryTimes(source.getRetryTimes());
    }

    private static void applyEnvironmentOverrides(AppConfig config) {
        // LLM provider
        Optional.ofNullable(System.getenv("JOBMATCH_LLM_PROVIDER"))
                .ifPresent(v -> config.getLlm().setProvider(v));

        // LLM API key
        Optional.ofNullable(System.getenv("JOBMATCH_LLM_API_KEY"))
                .or(() -> Optional.ofNullable(System.getenv("LLM_API_KEY")))
                .ifPresent(v -> config.getLlm().getCloud().setApiKey(v));

        // LLM model
        Optional.ofNullable(System.getenv("JOBMATCH_LLM_MODEL"))
                .ifPresent(v -> {
                    if ("local".equals(config.getLlm().getProvider())) {
                        config.getLlm().getLocal().setModel(v);
                    } else {
                        config.getLlm().getCloud().setModel(v);
                    }
                });

        // Data directory
        Optional.ofNullable(System.getenv("JOBMATCH_DATA_DIR"))
                .ifPresent(v -> config.getStorage().setDataDir(v));

        // Cache directory
        Optional.ofNullable(System.getenv("JOBMATCH_CACHE_DIR"))
                .ifPresent(v -> config.getStorage().setCacheDir(v));

        // Log level
        Optional.ofNullable(System.getenv("JOBMATCH_LOG_LEVEL"))
                .ifPresent(v -> config.getLogging().setLevel(v));
    }

    private static void expandPaths(AppConfig config) {
        config.getStorage().setDataDir(expandPath(config.getStorage().getDataDir()));
        config.getStorage().setCacheDir(expandPath(config.getStorage().getCacheDir()));
        config.getLogging().setFile(expandPath(config.getLogging().getFile()));
    }

    /**
     * Expand ~ to user home directory.
     */
    public static String expandPath(String path) {
        if (path == null) return null;
        if (path.startsWith("~")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
