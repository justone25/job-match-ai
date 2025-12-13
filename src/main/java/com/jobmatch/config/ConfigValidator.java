package com.jobmatch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates configuration files for correctness.
 * Checks matcher.yaml and skill_implications.yaml for required fields and valid values.
 */
public class ConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidator.class);

    /**
     * Validation result containing errors and warnings.
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String message) {
            errors.add(message);
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public int getErrorCount() {
            return errors.size();
        }

        public int getWarningCount() {
            return warnings.size();
        }
    }

    /**
     * Validate application config.
     */
    public static ValidationResult validateAppConfig(AppConfig config) {
        ValidationResult result = new ValidationResult();

        if (config == null) {
            result.addError("Application config is null");
            return result;
        }

        // Validate LLM config
        if (config.getLlm() == null) {
            result.addError("llm config is missing");
        } else {
            String provider = config.getLlm().getProvider();
            if (provider == null || provider.isEmpty()) {
                result.addError("llm.provider is not set");
            } else if (!"local".equals(provider) && !"cloud".equals(provider)) {
                result.addError("llm.provider must be 'local' or 'cloud', got: " + provider);
            }

            if ("local".equals(provider) && config.getLlm().getLocal() != null) {
                if (config.getLlm().getLocal().getBaseUrl() == null) {
                    result.addError("llm.local.base_url is not set");
                }
                if (config.getLlm().getLocal().getModel() == null) {
                    result.addError("llm.local.model is not set");
                }
            }

            if ("cloud".equals(provider) && config.getLlm().getCloud() != null) {
                String apiKey = config.getLlm().getCloud().getApiKey();
                if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("${")) {
                    result.addError("llm.cloud.api_key is not set");
                }
            }
        }

        // Validate storage config
        if (config.getStorage() == null) {
            result.addWarning("storage config is missing, defaults will be used");
        } else {
            if (config.getStorage().getCacheTtlDays() <= 0) {
                result.addWarning("storage.cache_ttl_days should be positive");
            }
        }

        return result;
    }

    /**
     * Validate matcher config.
     */
    public static ValidationResult validateMatcherConfig(MatcherConfig config) {
        ValidationResult result = new ValidationResult();

        if (config == null) {
            result.addError("Matcher config is null");
            return result;
        }

        // Validate hard_gate
        MatcherConfig.HardGateConfig hardGate = config.getHardGate();
        if (hardGate == null) {
            result.addError("hard_gate config is missing");
        } else {
            // Validate borderline
            if (hardGate.getBorderline() == null || hardGate.getBorderline().getDeltaYears() <= 0) {
                result.addWarning("hard_gate.borderline.delta_years should be positive");
            }

            // Validate confidence scores
            MatcherConfig.ConfidenceConfig conf = hardGate.getConfidence();
            if (conf != null) {
                validateConfidenceScore(result, "hard_gate.confidence.direct_match", conf.getDirectMatch());
                validateConfidenceScore(result, "hard_gate.confidence.implied_match", conf.getImpliedMatch());
                validateConfidenceScore(result, "hard_gate.confidence.keyword_match", conf.getKeywordMatch());
                validateConfidenceScore(result, "hard_gate.confidence.unknown", conf.getUnknown());
            }

            // Validate proficiency levels
            MatcherConfig.ProficiencyConfig prof = hardGate.getProficiency();
            if (prof == null || prof.getLevelMap() == null || prof.getLevelMap().isEmpty()) {
                result.addWarning("hard_gate.proficiency.level_map is empty or missing");
            }

            // Validate education levels
            MatcherConfig.EducationConfig edu = hardGate.getEducation();
            if (edu == null || edu.getLevelMap() == null || edu.getLevelMap().isEmpty()) {
                result.addWarning("hard_gate.education.level_map is empty or missing");
            }
        }

        // Validate soft_score
        MatcherConfig.SoftScoreConfig softScore = config.getSoftScore();
        if (softScore == null) {
            result.addError("soft_score config is missing");
        } else {
            // Validate weights
            MatcherConfig.WeightsConfig weights = softScore.getWeights();
            if (weights != null) {
                double total = weights.getSkill() + weights.getExperience() + weights.getBonus();
                if (Math.abs(total - 1.0) > 0.01) {
                    result.addWarning("soft_score.weights should sum to 1.0, got: " + total);
                }
            }

            // Validate skill score config
            MatcherConfig.SkillScoreConfig skill = softScore.getSkill();
            if (skill != null) {
                if (skill.getBasePoint() <= 0) {
                    result.addWarning("soft_score.skill.base_point should be positive");
                }
                if (skill.getInferenceMultiplier() <= 0 || skill.getInferenceMultiplier() > 1) {
                    result.addWarning("soft_score.skill.inference_multiplier should be between 0 and 1");
                }
            }

            // Validate keywords
            if (softScore.getIndustryKeywords() == null || softScore.getIndustryKeywords().isEmpty()) {
                result.addWarning("soft_score.industry_keywords is empty");
            }
            if (softScore.getProjectKeywords() == null || softScore.getProjectKeywords().isEmpty()) {
                result.addWarning("soft_score.project_keywords is empty");
            }
        }

        // Validate gap_analyzer
        MatcherConfig.GapAnalyzerConfig gapAnalyzer = config.getGapAnalyzer();
        if (gapAnalyzer == null) {
            result.addWarning("gap_analyzer config is missing");
        } else {
            if (gapAnalyzer.getValuableCategories() == null || gapAnalyzer.getValuableCategories().isEmpty()) {
                result.addWarning("gap_analyzer.valuable_categories is empty");
            }
            if (gapAnalyzer.getSuggestionTemplates() == null || gapAnalyzer.getSuggestionTemplates().isEmpty()) {
                result.addWarning("gap_analyzer.suggestion_templates is empty");
            }
        }

        return result;
    }

    /**
     * Validate skill implication config.
     */
    public static ValidationResult validateSkillImplicationConfig(SkillImplicationConfig config) {
        ValidationResult result = new ValidationResult();

        if (config == null) {
            result.addError("Skill implication config is null");
            return result;
        }

        // Validate version
        if (config.getVersion() == null || config.getVersion().isEmpty()) {
            result.addWarning("version is not set");
        }

        // Validate rules
        if (config.getRules() == null || config.getRules().isEmpty()) {
            result.addWarning("rules list is empty - no skill implications will be applied");
        } else {
            int ruleIndex = 0;
            for (SkillImplicationConfig.ImplicationRule rule : config.getRules()) {
                String prefix = "rules[" + ruleIndex + "]";

                if (rule.getId() == null || rule.getId().isEmpty()) {
                    result.addWarning(prefix + ".id is not set");
                }

                if (rule.getTriggers() == null || rule.getTriggers().isEmpty()) {
                    result.addError(prefix + ".triggers is empty");
                }

                if (rule.getImplies() == null || rule.getImplies().isEmpty()) {
                    result.addError(prefix + ".implies is empty");
                }

                if (rule.getConfidence() <= 0 || rule.getConfidence() > 1) {
                    result.addWarning(prefix + ".confidence should be between 0 and 1, got: " + rule.getConfidence());
                }

                ruleIndex++;
            }
        }

        // Validate settings
        SkillImplicationConfig.Settings settings = config.getSettings();
        if (settings != null) {
            if (settings.getDefaultConfidence() <= 0 || settings.getDefaultConfidence() > 1) {
                result.addWarning("settings.default_confidence should be between 0 and 1");
            }

            SkillImplicationConfig.LlmFallbackConfig llm = settings.getLlmFallback();
            if (llm != null && llm.isEnabled()) {
                if (llm.getMinConfidence() <= 0 || llm.getMinConfidence() > 1) {
                    result.addWarning("settings.llm_fallback.min_confidence should be between 0 and 1");
                }
                if (llm.getCacheTtlDays() <= 0) {
                    result.addWarning("settings.llm_fallback.cache_ttl_days should be positive");
                }
                if (llm.getPromptTemplate() == null || llm.getPromptTemplate().isEmpty()) {
                    result.addWarning("settings.llm_fallback.prompt_template is empty");
                }
            }
        }

        return result;
    }

    /**
     * Validate all configs and return combined result.
     */
    public static ValidationResult validateAll() {
        ValidationResult combined = new ValidationResult();

        try {
            AppConfig appConfig = ConfigLoader.load();
            ValidationResult appResult = validateAppConfig(appConfig);
            combined.getErrors().addAll(appResult.getErrors());
            combined.getWarnings().addAll(appResult.getWarnings());
        } catch (Exception e) {
            combined.addError("Failed to load application config: " + e.getMessage());
        }

        try {
            MatcherConfig matcherConfig = MatcherConfigLoader.getInstance().getConfig();
            ValidationResult matcherResult = validateMatcherConfig(matcherConfig);
            combined.getErrors().addAll(matcherResult.getErrors());
            combined.getWarnings().addAll(matcherResult.getWarnings());
        } catch (Exception e) {
            combined.addError("Failed to load matcher config: " + e.getMessage());
        }

        try {
            SkillImplicationConfig implConfig = SkillImplicationLoader.getInstance().getConfig();
            ValidationResult implResult = validateSkillImplicationConfig(implConfig);
            combined.getErrors().addAll(implResult.getErrors());
            combined.getWarnings().addAll(implResult.getWarnings());
        } catch (Exception e) {
            combined.addError("Failed to load skill implication config: " + e.getMessage());
        }

        if (combined.isValid()) {
            log.info("All configs validated successfully: {} warnings", combined.getWarningCount());
        } else {
            log.warn("Config validation failed: {} errors, {} warnings",
                    combined.getErrorCount(), combined.getWarningCount());
        }

        return combined;
    }

    private static void validateConfidenceScore(ValidationResult result, String field, double value) {
        if (value < 0 || value > 1) {
            result.addWarning(field + " should be between 0 and 1, got: " + value);
        }
    }
}
