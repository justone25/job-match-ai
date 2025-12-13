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

/**
 * Loader for matcher configuration with priority-based merging.
 *
 * Priority (low to high):
 * 1. Built-in defaults (/matcher.yaml in classpath)
 * 2. User home config (~/.jobmatch/matcher.yaml)
 * 3. Current directory config (./matcher.yaml)
 * 4. Environment variable path (JOBMATCH_MATCHER_CONFIG)
 */
public class MatcherConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(MatcherConfigLoader.class);
    private static final String DEFAULT_CONFIG_PATH = "/matcher.yaml";
    private static final String USER_CONFIG_FILENAME = "matcher.yaml";
    private static final String ENV_CONFIG_PATH = "JOBMATCH_MATCHER_CONFIG";

    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static volatile MatcherConfigLoader instance;
    private MatcherConfig config;

    /**
     * Get singleton instance.
     */
    public static MatcherConfigLoader getInstance() {
        if (instance == null) {
            synchronized (MatcherConfigLoader.class) {
                if (instance == null) {
                    instance = new MatcherConfigLoader();
                }
            }
        }
        return instance;
    }

    /**
     * Create loader and load configuration.
     */
    public MatcherConfigLoader() {
        this.config = load();
    }

    /**
     * Load configuration from all sources.
     */
    private MatcherConfig load() {
        // Start with defaults
        MatcherConfig config = new MatcherConfig();

        // Load in priority order (later sources override earlier ones)
        loadFromBuiltinDefaults(config);
        loadFromUserHome(config);
        loadFromCurrentDir(config);
        loadFromEnvConfigPath(config);

        log.info("Matcher config loaded: version={}", config.getVersion());
        return config;
    }

    /**
     * Reload configuration.
     */
    public MatcherConfig reload() {
        this.config = load();
        return this.config;
    }

    /**
     * Get current configuration.
     */
    public MatcherConfig getConfig() {
        return config;
    }

    /**
     * Get configuration version.
     */
    public String getVersion() {
        return config.getVersion();
    }

    // Private loading methods

    private void loadFromBuiltinDefaults(MatcherConfig config) {
        try (InputStream is = getClass().getResourceAsStream(DEFAULT_CONFIG_PATH)) {
            if (is != null) {
                MatcherConfig fileConfig = yamlMapper.readValue(is, MatcherConfig.class);
                mergeConfig(config, fileConfig);
                log.debug("Loaded built-in matcher config");
            }
        } catch (IOException e) {
            log.debug("No built-in matcher config found: {}", e.getMessage());
        }
    }

    private void loadFromUserHome(MatcherConfig config) {
        String userHome = System.getProperty("user.home");
        Path configPath = Paths.get(userHome, ".jobmatch", USER_CONFIG_FILENAME);
        loadFromPath(config, configPath);
    }

    private void loadFromCurrentDir(MatcherConfig config) {
        Path configPath = Paths.get(USER_CONFIG_FILENAME);
        loadFromPath(config, configPath);
    }

    private void loadFromEnvConfigPath(MatcherConfig config) {
        String envPath = System.getenv(ENV_CONFIG_PATH);
        if (envPath != null && !envPath.isEmpty()) {
            Path configPath = Paths.get(envPath);
            loadFromPath(config, configPath);
        }
    }

    private void loadFromPath(MatcherConfig config, Path path) {
        if (Files.exists(path)) {
            try {
                MatcherConfig fileConfig = yamlMapper.readValue(path.toFile(), MatcherConfig.class);
                mergeConfig(config, fileConfig);
                log.debug("Loaded matcher config from: {}", path);
            } catch (IOException e) {
                log.warn("Failed to load matcher config from {}: {}", path, e.getMessage());
            }
        }
    }

    // Merge methods

    private void mergeConfig(MatcherConfig target, MatcherConfig source) {
        if (source == null) return;

        if (source.getVersion() != null) {
            target.setVersion(source.getVersion());
        }
        if (source.getUpdatedAt() != null) {
            target.setUpdatedAt(source.getUpdatedAt());
        }

        if (source.getHardGate() != null) {
            mergeHardGateConfig(target.getHardGate(), source.getHardGate());
        }
        if (source.getSoftScore() != null) {
            mergeSoftScoreConfig(target.getSoftScore(), source.getSoftScore());
        }
        if (source.getGapAnalyzer() != null) {
            mergeGapAnalyzerConfig(target.getGapAnalyzer(), source.getGapAnalyzer());
        }
        if (source.getActionGenerator() != null) {
            mergeActionGeneratorConfig(target.getActionGenerator(), source.getActionGenerator());
        }
        if (source.getReport() != null) {
            mergeReportConfig(target.getReport(), source.getReport());
        }
    }

    private void mergeHardGateConfig(MatcherConfig.HardGateConfig target, MatcherConfig.HardGateConfig source) {
        if (source.getBorderline() != null && source.getBorderline().getDeltaYears() > 0) {
            target.getBorderline().setDeltaYears(source.getBorderline().getDeltaYears());
        }

        if (source.getConfidence() != null) {
            MatcherConfig.ConfidenceConfig tc = target.getConfidence();
            MatcherConfig.ConfidenceConfig sc = source.getConfidence();
            if (sc.getDirectMatch() > 0) tc.setDirectMatch(sc.getDirectMatch());
            if (sc.getImpliedMatch() > 0) tc.setImpliedMatch(sc.getImpliedMatch());
            if (sc.getKeywordMatch() > 0) tc.setKeywordMatch(sc.getKeywordMatch());
            if (sc.getUnknown() > 0) tc.setUnknown(sc.getUnknown());
        }

        if (source.getDuration() != null && source.getDuration().getDefaultMonths() > 0) {
            target.getDuration().setDefaultMonths(source.getDuration().getDefaultMonths());
        }

        if (source.getProficiency() != null) {
            if (source.getProficiency().getLevelMap() != null) {
                target.getProficiency().getLevelMap().putAll(source.getProficiency().getLevelMap());
            }
            if (source.getProficiency().getAliases() != null) {
                target.getProficiency().getAliases().putAll(source.getProficiency().getAliases());
            }
        }

        if (source.getEducation() != null) {
            if (source.getEducation().getLevelMap() != null) {
                target.getEducation().getLevelMap().putAll(source.getEducation().getLevelMap());
            }
            if (source.getEducation().getAliases() != null) {
                target.getEducation().getAliases().putAll(source.getEducation().getAliases());
            }
        }

        if (source.getUnknownPolicy() != null) {
            if (source.getUnknownPolicy().getDefaultStatus() != null) {
                target.getUnknownPolicy().setDefaultStatus(source.getUnknownPolicy().getDefaultStatus());
            }
            target.getUnknownPolicy().setLogWarning(source.getUnknownPolicy().isLogWarning());
        }
    }

    private void mergeSoftScoreConfig(MatcherConfig.SoftScoreConfig target, MatcherConfig.SoftScoreConfig source) {
        if (source.getWeights() != null) {
            MatcherConfig.WeightsConfig tw = target.getWeights();
            MatcherConfig.WeightsConfig sw = source.getWeights();
            if (sw.getSkill() > 0) tw.setSkill(sw.getSkill());
            if (sw.getExperience() > 0) tw.setExperience(sw.getExperience());
            if (sw.getBonus() > 0) tw.setBonus(sw.getBonus());
        }

        if (source.getSkill() != null) {
            MatcherConfig.SkillScoreConfig ts = target.getSkill();
            MatcherConfig.SkillScoreConfig ss = source.getSkill();
            if (ss.getBasePoint() > 0) ts.setBasePoint(ss.getBasePoint());
            if (ss.getInferenceMultiplier() > 0) ts.setInferenceMultiplier(ss.getInferenceMultiplier());
            if (ss.getPartialMultiplier() > 0) ts.setPartialMultiplier(ss.getPartialMultiplier());
        }

        if (source.getIndustryKeywords() != null && !source.getIndustryKeywords().isEmpty()) {
            target.setIndustryKeywords(source.getIndustryKeywords());
        }

        if (source.getProjectKeywords() != null && !source.getProjectKeywords().isEmpty()) {
            target.setProjectKeywords(source.getProjectKeywords());
        }
    }

    private void mergeGapAnalyzerConfig(MatcherConfig.GapAnalyzerConfig target, MatcherConfig.GapAnalyzerConfig source) {
        if (source.getImpactLevels() != null && !source.getImpactLevels().isEmpty()) {
            target.getImpactLevels().putAll(source.getImpactLevels());
        }

        if (source.getValuableCategories() != null && !source.getValuableCategories().isEmpty()) {
            target.setValuableCategories(source.getValuableCategories());
        }

        if (source.getSuggestionTemplates() != null && !source.getSuggestionTemplates().isEmpty()) {
            target.getSuggestionTemplates().putAll(source.getSuggestionTemplates());
        }
    }

    private void mergeActionGeneratorConfig(MatcherConfig.ActionGeneratorConfig target, MatcherConfig.ActionGeneratorConfig source) {
        if (source.getEditTypes() != null && !source.getEditTypes().isEmpty()) {
            target.getEditTypes().putAll(source.getEditTypes());
        }

        if (source.getLearningPlan() != null) {
            MatcherConfig.LearningPlanConfig tl = target.getLearningPlan();
            MatcherConfig.LearningPlanConfig sl = source.getLearningPlan();
            if (sl.getDefaultDuration() != null) tl.setDefaultDuration(sl.getDefaultDuration());
            if (sl.getMaxFocusAreas() > 0) tl.setMaxFocusAreas(sl.getMaxFocusAreas());
            if (sl.getHoursPerDay() > 0) tl.setHoursPerDay(sl.getHoursPerDay());
        }
    }

    private void mergeReportConfig(MatcherConfig.ReportConfig target, MatcherConfig.ReportConfig source) {
        target.setIncludeConfigVersion(source.isIncludeConfigVersion());
        target.setIncludeRulesVersion(source.isIncludeRulesVersion());
    }
}
