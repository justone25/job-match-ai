package com.jobmatch.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Loader for skill implication rules with priority-based merging.
 *
 * Priority (low to high):
 * 1. Built-in defaults (/skill_implications.yaml in classpath)
 * 2. User home config (~/.jobmatch/skill_implications.yaml)
 * 3. Current directory config (./skill_implications.yaml)
 * 4. Environment variable path (JOBMATCH_IMPLICATION_CONFIG)
 */
public class SkillImplicationLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillImplicationLoader.class);
    private static final String DEFAULT_CONFIG_PATH = "/skill_implications.yaml";
    private static final String USER_CONFIG_FILENAME = "skill_implications.yaml";
    private static final String ENV_CONFIG_PATH = "JOBMATCH_IMPLICATION_CONFIG";

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static volatile SkillImplicationLoader instance;
    private SkillImplicationConfig config;

    // Indexes for fast lookup
    private Map<String, List<SkillImplicationConfig.ImplicationRule>> triggerIndex;
    private Map<String, List<String>> categoryToSkillsIndex;

    /**
     * Get singleton instance.
     */
    public static SkillImplicationLoader getInstance() {
        if (instance == null) {
            synchronized (SkillImplicationLoader.class) {
                if (instance == null) {
                    instance = new SkillImplicationLoader();
                }
            }
        }
        return instance;
    }

    /**
     * Create loader and load configuration.
     */
    public SkillImplicationLoader() {
        this.config = load();
        buildIndexes();
    }

    /**
     * Load configuration from all sources.
     */
    private SkillImplicationConfig load() {
        // Start with defaults
        SkillImplicationConfig config = new SkillImplicationConfig();

        // Load in priority order
        loadFromBuiltinDefaults(config);
        loadFromUserHome(config);
        loadFromCurrentDir(config);
        loadFromEnvConfigPath(config);

        log.info("Skill implication config loaded: version={}, rules={}",
                config.getVersion(), config.getRuleCount());
        return config;
    }

    /**
     * Reload configuration.
     */
    public SkillImplicationConfig reload() {
        this.config = load();
        buildIndexes();
        return this.config;
    }

    /**
     * Get current configuration.
     */
    public SkillImplicationConfig getConfig() {
        return config;
    }

    /**
     * Get configuration version.
     */
    public String getVersion() {
        return config.getVersion();
    }

    /**
     * Get rules that can be triggered by a specific skill.
     */
    public List<SkillImplicationConfig.ImplicationRule> getRulesForTrigger(String skill) {
        if (skill == null) return Collections.emptyList();
        return triggerIndex.getOrDefault(skill.toLowerCase(), Collections.emptyList());
    }

    /**
     * Get all skills that can imply a given category/skill.
     */
    public List<String> getSkillsImplyingCategory(String category) {
        if (category == null) return Collections.emptyList();
        return categoryToSkillsIndex.getOrDefault(category.toLowerCase(), Collections.emptyList());
    }

    /**
     * Get LLM fallback settings.
     */
    public SkillImplicationConfig.LlmFallbackConfig getLlmFallbackConfig() {
        return config.getSettings().getLlmFallback();
    }

    /**
     * Check if LLM fallback is enabled.
     */
    public boolean isLlmFallbackEnabled() {
        return config.getSettings().getLlmFallback().isEnabled();
    }

    // Private loading methods

    private void loadFromBuiltinDefaults(SkillImplicationConfig config) {
        try (InputStream is = getClass().getResourceAsStream(DEFAULT_CONFIG_PATH)) {
            if (is != null) {
                SkillImplicationConfig fileConfig = yamlMapper.readValue(is, SkillImplicationConfig.class);
                mergeConfig(config, fileConfig);
                log.debug("Loaded built-in skill implication config");
            }
        } catch (IOException e) {
            log.debug("No built-in skill implication config found: {}", e.getMessage());
        }
    }

    private void loadFromUserHome(SkillImplicationConfig config) {
        String userHome = System.getProperty("user.home");
        Path configPath = Paths.get(userHome, ".jobmatch", USER_CONFIG_FILENAME);
        loadFromPath(config, configPath);
    }

    private void loadFromCurrentDir(SkillImplicationConfig config) {
        Path configPath = Paths.get(USER_CONFIG_FILENAME);
        loadFromPath(config, configPath);
    }

    private void loadFromEnvConfigPath(SkillImplicationConfig config) {
        String envPath = System.getenv(ENV_CONFIG_PATH);
        if (envPath != null && !envPath.isEmpty()) {
            Path configPath = Paths.get(envPath);
            loadFromPath(config, configPath);
        }
    }

    private void loadFromPath(SkillImplicationConfig config, Path path) {
        if (Files.exists(path)) {
            try {
                SkillImplicationConfig fileConfig = yamlMapper.readValue(path.toFile(), SkillImplicationConfig.class);
                mergeConfig(config, fileConfig);
                log.debug("Loaded skill implication config from: {}", path);
            } catch (IOException e) {
                log.warn("Failed to load skill implication config from {}: {}", path, e.getMessage());
            }
        }
    }

    // Merge methods

    private void mergeConfig(SkillImplicationConfig target, SkillImplicationConfig source) {
        if (source == null) return;

        if (source.getVersion() != null) {
            target.setVersion(source.getVersion());
        }
        if (source.getUpdatedAt() != null) {
            target.setUpdatedAt(source.getUpdatedAt());
        }

        // Merge rules
        if (source.getRules() != null && !source.getRules().isEmpty()) {
            mergeRules(target, source.getRules());
        }

        // Merge settings
        if (source.getSettings() != null) {
            mergeSettings(target.getSettings(), source.getSettings());
        }
    }

    private void mergeRules(SkillImplicationConfig target, List<SkillImplicationConfig.ImplicationRule> sourceRules) {
        boolean allowOverride = target.getSettings().isAllowUserOverride();

        // Build map of existing rules by ID
        Map<String, SkillImplicationConfig.ImplicationRule> ruleMap = new LinkedHashMap<>();
        for (SkillImplicationConfig.ImplicationRule rule : target.getRules()) {
            if (rule.getId() != null) {
                ruleMap.put(rule.getId(), rule);
            }
        }

        // Merge source rules
        for (SkillImplicationConfig.ImplicationRule rule : sourceRules) {
            if (rule.getId() != null) {
                if (allowOverride || !ruleMap.containsKey(rule.getId())) {
                    ruleMap.put(rule.getId(), rule);
                }
            } else {
                // Rules without ID are always added
                target.getRules().add(rule);
            }
        }

        // Replace rules list with merged result
        target.setRules(new ArrayList<>(ruleMap.values()));

        // Sort by priority
        target.getRules().sort(Comparator.comparingInt(SkillImplicationConfig.ImplicationRule::getPriority));
    }

    private void mergeSettings(SkillImplicationConfig.Settings target, SkillImplicationConfig.Settings source) {
        if (source.getDefaultConfidence() > 0) {
            target.setDefaultConfidence(source.getDefaultConfidence());
        }
        target.setEnableReverseMapping(source.isEnableReverseMapping());
        target.setAllowUserOverride(source.isAllowUserOverride());

        if (source.getLlmFallback() != null) {
            SkillImplicationConfig.LlmFallbackConfig tl = target.getLlmFallback();
            SkillImplicationConfig.LlmFallbackConfig sl = source.getLlmFallback();
            tl.setEnabled(sl.isEnabled());
            if (sl.getMinConfidence() > 0) tl.setMinConfidence(sl.getMinConfidence());
            if (sl.getCacheTtlDays() > 0) tl.setCacheTtlDays(sl.getCacheTtlDays());
            if (sl.getPromptTemplate() != null) tl.setPromptTemplate(sl.getPromptTemplate());
        }
    }

    // Index building

    private void buildIndexes() {
        triggerIndex = new HashMap<>();
        categoryToSkillsIndex = new HashMap<>();

        for (SkillImplicationConfig.ImplicationRule rule : config.getRules()) {
            // Build trigger index
            if (rule.getTriggers() != null) {
                for (String trigger : rule.getTriggers()) {
                    String key = trigger.toLowerCase();
                    triggerIndex.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
            }

            // Build reverse mapping (category -> skills)
            if (config.getSettings().isEnableReverseMapping() && rule.getImplies() != null) {
                for (String implied : rule.getImplies()) {
                    String key = implied.toLowerCase();
                    if (rule.getTriggers() != null) {
                        for (String trigger : rule.getTriggers()) {
                            List<String> skills = categoryToSkillsIndex.computeIfAbsent(key, k -> new ArrayList<>());
                            if (!skills.contains(trigger.toLowerCase())) {
                                skills.add(trigger.toLowerCase());
                            }
                        }
                    }
                }
            }
        }

        log.debug("Built indexes: {} trigger entries, {} category entries",
                triggerIndex.size(), categoryToSkillsIndex.size());
    }
}
