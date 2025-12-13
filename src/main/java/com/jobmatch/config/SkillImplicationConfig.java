package com.jobmatch.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for skill implication rules.
 * Maps to skill_implications.yaml configuration file.
 */
@Data
public class SkillImplicationConfig {

    private String version = "1.0.0";
    private String updatedAt;

    private List<ImplicationRule> rules = new ArrayList<>();
    private Settings settings = new Settings();

    /**
     * A single implication rule.
     */
    @Data
    public static class ImplicationRule {
        private String id;
        private List<String> triggers = new ArrayList<>();
        private List<String> implies = new ArrayList<>();
        private double confidence = 0.75;
        private String evidenceTemplate = "具备 {trigger} 经验，可推断掌握 {implied}";
        private int priority = 10;

        /**
         * Generate evidence string from template.
         */
        public String generateEvidence(String trigger, String implied) {
            return evidenceTemplate
                    .replace("{trigger}", trigger)
                    .replace("{implied}", implied);
        }
    }

    /**
     * Global settings for skill implication.
     */
    @Data
    public static class Settings {
        private double defaultConfidence = 0.75;
        private boolean enableReverseMapping = true;
        private boolean allowUserOverride = true;
        private LlmFallbackConfig llmFallback = new LlmFallbackConfig();
    }

    /**
     * LLM fallback configuration.
     */
    @Data
    public static class LlmFallbackConfig {
        private boolean enabled = true;
        private double minConfidence = 0.60;
        private int cacheTtlDays = 7;
        private String promptTemplate = """
            判断技能"{candidate_skill}"是否可以推断候选人具备"{required_skill}"能力。

            分析要点：
            1. 这两个技能是否属于同一技术栈或生态系统？
            2. 掌握前者是否通常意味着具备后者的能力？
            3. 在实际工作中，具备前者技能的人是否通常也会使用后者？

            请以JSON格式返回结果：
            {"can_imply": true/false, "confidence": 0.0-1.0, "reasoning": "简短解释"}
            """;
    }

    /**
     * Get rule count.
     */
    public int getRuleCount() {
        return rules != null ? rules.size() : 0;
    }

    /**
     * Get all trigger skills from all rules.
     */
    public List<String> getAllTriggers() {
        List<String> allTriggers = new ArrayList<>();
        if (rules != null) {
            for (ImplicationRule rule : rules) {
                if (rule.getTriggers() != null) {
                    allTriggers.addAll(rule.getTriggers());
                }
            }
        }
        return allTriggers;
    }
}
