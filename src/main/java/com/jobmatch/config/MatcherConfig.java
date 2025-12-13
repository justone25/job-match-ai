package com.jobmatch.config;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for matcher components.
 * Maps to matcher.yaml configuration file.
 */
@Data
public class MatcherConfig {

    private String version = "1.0.0";
    private String updatedAt;

    private HardGateConfig hardGate = new HardGateConfig();
    private SoftScoreConfig softScore = new SoftScoreConfig();
    private GapAnalyzerConfig gapAnalyzer = new GapAnalyzerConfig();
    private ActionGeneratorConfig actionGenerator = new ActionGeneratorConfig();
    private ReportConfig report = new ReportConfig();

    /**
     * Hard gate checker configuration.
     */
    @Data
    public static class HardGateConfig {
        private BorderlineConfig borderline = new BorderlineConfig();
        private ConfidenceConfig confidence = new ConfidenceConfig();
        private DurationConfig duration = new DurationConfig();
        private ProficiencyConfig proficiency = new ProficiencyConfig();
        private EducationConfig education = new EducationConfig();
        private UnknownPolicyConfig unknownPolicy = new UnknownPolicyConfig();
    }

    @Data
    public static class BorderlineConfig {
        // Years delta to consider as borderline (e.g., requires 5 years, has 4+ years)
        private int deltaYears = 1;
    }

    @Data
    public static class ConfidenceConfig {
        private double directMatch = 0.90;
        private double impliedMatch = 0.75;
        private double keywordMatch = 0.70;
        private double unknown = 0.30;
    }

    @Data
    public static class DurationConfig {
        // Default months when cannot parse duration string
        private int defaultMonths = 12;
    }

    @Data
    public static class ProficiencyConfig {
        private Map<String, Integer> levelMap = new HashMap<>() {{
            put("expert", 4);
            put("proficient", 3);
            put("familiar", 2);
            put("beginner", 1);
        }};

        private Map<String, String> aliases = new HashMap<>() {{
            put("精通", "expert");
            put("专家", "expert");
            put("熟练", "proficient");
            put("熟悉", "proficient");
            put("了解", "familiar");
            put("掌握", "familiar");
            put("入门", "beginner");
            put("初学", "beginner");
        }};
    }

    @Data
    public static class EducationConfig {
        private Map<String, Integer> levelMap = new HashMap<>() {{
            put("phd", 5);
            put("master", 4);
            put("bachelor", 3);
            put("associate", 2);
            put("high_school", 1);
        }};

        private Map<String, String> aliases = new HashMap<>() {{
            put("博士", "phd");
            put("doctor", "phd");
            put("硕士", "master");
            put("研究生", "master");
            put("本科", "bachelor");
            put("学士", "bachelor");
            put("大专", "associate");
            put("专科", "associate");
            put("高中", "high_school");
            put("中专", "high_school");
        }};
    }

    @Data
    public static class UnknownPolicyConfig {
        // How to treat unknown requirement types: SKIP, FAIL, UNKNOWN
        private String defaultStatus = "UNKNOWN";
        private boolean logWarning = true;
    }

    /**
     * Soft score calculator configuration.
     */
    @Data
    public static class SoftScoreConfig {
        private WeightsConfig weights = new WeightsConfig();
        private SkillScoreConfig skill = new SkillScoreConfig();
        private ExperienceScoreConfig experience = new ExperienceScoreConfig();
        private BonusScoreConfig bonus = new BonusScoreConfig();
        private List<String> industryKeywords = List.of(
                "金融", "互联网", "电商", "教育", "医疗", "游戏", "物流"
        );
        private List<String> projectKeywords = List.of(
                "微服务", "分布式", "高并发", "架构", "优化", "性能"
        );
    }

    @Data
    public static class WeightsConfig {
        private double skill = 0.60;
        private double experience = 0.30;
        private double bonus = 0.10;
    }

    @Data
    public static class SkillScoreConfig {
        private int basePoint = 10;
        private double inferenceMultiplier = 0.80;
        private double partialMultiplier = 0.50;
    }

    @Data
    public static class ExperienceScoreConfig {
        private ThresholdsConfig thresholds = new ThresholdsConfig();
    }

    @Data
    public static class ThresholdsConfig {
        private int matched = 70;
        private int partial = 40;
    }

    @Data
    public static class BonusScoreConfig {
        private int bonusSkillPoints = 15;
        private int normalSkillPoints = 10;
        private int uniqueStrengthMax = 20;
        private int expertSkillBonus = 5;
    }

    /**
     * Gap analyzer configuration.
     */
    @Data
    public static class GapAnalyzerConfig {
        private Map<String, ImpactLevelConfig> impactLevels = new HashMap<>() {{
            put("high", new ImpactLevelConfig("high", "核心要求，必须满足"));
            put("medium", new ImpactLevelConfig("medium", "重要要求，建议满足"));
            put("low", new ImpactLevelConfig("low", "加分项，非必须"));
        }};
        private List<String> valuableCategories = List.of("bigdata", "ai_ml", "cloud");
        private Map<String, String> suggestionTemplates = new HashMap<>() {{
            put("backend", "建议通过项目实践学习 {skill}，可以从官方文档和在线教程入手");
            put("frontend", "建议通过构建小项目学习 {skill}，注重实践");
            put("database", "建议在本地搭建 {skill} 环境，通过实际操作学习");
            put("devops", "建议在云平台或本地虚拟机上实践 {skill}");
            put("cloud", "建议申请免费试用账号，实际操作 {skill}");
            put("default", "建议系统学习 {skill}");
        }};
    }

    @Data
    public static class ImpactLevelConfig {
        private String label;
        private String description;

        public ImpactLevelConfig() {}

        public ImpactLevelConfig(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /**
     * Action generator configuration.
     */
    @Data
    public static class ActionGeneratorConfig {
        private Map<String, String> editTypes = new HashMap<>() {{
            put("highlight", "突出核心优势");
            put("modify", "修改现有内容");
            put("add", "添加新内容");
        }};
        private LearningPlanConfig learningPlan = new LearningPlanConfig();
    }

    @Data
    public static class LearningPlanConfig {
        private String defaultDuration = "1周";
        private int maxFocusAreas = 5;
        private int hoursPerDay = 2;
    }

    /**
     * Report configuration.
     */
    @Data
    public static class ReportConfig {
        private boolean includeConfigVersion = true;
        private boolean includeRulesVersion = true;
    }

    // Helper methods

    /**
     * Get proficiency score for a given level string.
     */
    public int getProficiencyScore(String level) {
        if (level == null) return 2; // Default to familiar

        String normalized = hardGate.proficiency.aliases.getOrDefault(level, level.toLowerCase());
        return hardGate.proficiency.levelMap.getOrDefault(normalized, 2);
    }

    /**
     * Get education score for a given education string.
     */
    public int getEducationScore(String education) {
        if (education == null) return 0;

        String lower = education.toLowerCase();
        for (Map.Entry<String, String> entry : hardGate.education.aliases.entrySet()) {
            if (lower.contains(entry.getKey().toLowerCase())) {
                return hardGate.education.levelMap.getOrDefault(entry.getValue(), 0);
            }
        }
        return 0;
    }

    /**
     * Get suggestion template for a skill category.
     */
    public String getSuggestionTemplate(String category) {
        return gapAnalyzer.suggestionTemplates.getOrDefault(
                category,
                gapAnalyzer.suggestionTemplates.get("default")
        );
    }

    /**
     * Get suggestion template for a skill, with skill name substitution.
     */
    public String getSuggestionTemplate(String category, String skill) {
        String template = getSuggestionTemplate(category);
        if (template != null && skill != null) {
            return template.replace("{skill}", skill);
        }
        return template != null ? template : "建议学习 " + skill + " 相关知识";
    }
}
