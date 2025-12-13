package com.jobmatch.matcher;

import com.jobmatch.config.MatcherConfig;
import com.jobmatch.config.MatcherConfigLoader;
import com.jobmatch.model.jd.HardRequirement;
import com.jobmatch.model.jd.JDParsed;
import com.jobmatch.model.jd.SoftRequirement;
import com.jobmatch.model.match.ScoreDetail;
import com.jobmatch.model.match.SoftScoreResult;
import com.jobmatch.model.resume.Experience;
import com.jobmatch.model.resume.Project;
import com.jobmatch.model.resume.ResumeParsed;
import com.jobmatch.model.resume.Skill;
import com.jobmatch.skill.SkillDictionaryService;
import com.jobmatch.skill.SkillImplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates soft score based on skill, experience, and bonus matching.
 * Uses configuration from matcher.yaml for weights and scoring parameters.
 * Based on PRD v3.2 section 13.2.2.
 */
public class SoftScoreCalculator {

    private static final Logger log = LoggerFactory.getLogger(SoftScoreCalculator.class);

    private final SkillDictionaryService skillService;
    private final SkillImplicationService implicationService;
    private final MatcherConfig config;

    public SoftScoreCalculator() {
        this(SkillDictionaryService.getInstance(), new SkillImplicationService(),
                MatcherConfigLoader.getInstance().getConfig());
    }

    public SoftScoreCalculator(SkillDictionaryService skillService) {
        this(skillService, new SkillImplicationService(),
                MatcherConfigLoader.getInstance().getConfig());
    }

    public SoftScoreCalculator(SkillImplicationService implicationService) {
        this(SkillDictionaryService.getInstance(), implicationService,
                MatcherConfigLoader.getInstance().getConfig());
    }

    /**
     * Full constructor for testing.
     */
    public SoftScoreCalculator(SkillDictionaryService skillService,
                               SkillImplicationService implicationService,
                               MatcherConfig config) {
        this.skillService = skillService;
        this.implicationService = implicationService;
        this.config = config;
    }

    /**
     * Calculate soft scores.
     */
    public SoftScoreResult calculate(ResumeParsed resume, JDParsed jd) {
        ScoreDetail skillScore = calculateSkillScore(resume, jd);
        ScoreDetail experienceScore = calculateExperienceScore(resume, jd);
        ScoreDetail bonusScore = calculateBonusScore(resume, jd);

        SoftScoreResult result = SoftScoreResult.calculate(skillScore, experienceScore, bonusScore);

        log.info("Soft score calculated: skill={} ({}), experience={} ({}), bonus={} ({}), total={}",
                skillScore.getScore(), skillScore.getWeight(),
                experienceScore.getScore(), experienceScore.getWeight(),
                bonusScore.getScore(), bonusScore.getWeight(),
                result.getFinalScore());

        return result;
    }

    /**
     * Calculate skill matching score.
     */
    private ScoreDetail calculateSkillScore(ResumeParsed resume, JDParsed jd) {
        List<ScoreDetail.ScoreItem> items = new ArrayList<>();
        int totalPoints = 0;
        int maxPoints = 0;

        // Get skill scoring config
        MatcherConfig.SkillScoreConfig skillConfig = config.getSoftScore().getSkill();
        int basePoint = skillConfig.getBasePoint();
        double inferenceMultiplier = skillConfig.getInferenceMultiplier();
        double partialMultiplier = skillConfig.getPartialMultiplier();

        // Get required skills from JD
        Set<String> requiredSkills = new HashSet<>();
        for (HardRequirement req : jd.getHardRequirements()) {
            if (HardRequirement.TYPE_SKILL.equals(req.getType())) {
                String skill = req.getStandardName() != null ? req.getStandardName() :
                        skillService.standardize(req.getSkill());
                requiredSkills.add(skill.toLowerCase());
            }
        }

        // Get candidate skills
        Set<String> candidateSkills = new HashSet<>();
        if (resume.getSkills() != null) {
            for (Skill skill : resume.getSkills()) {
                String standard = skill.getStandardName() != null ? skill.getStandardName() :
                        skillService.standardize(skill.getName());
                candidateSkills.add(standard.toLowerCase());
            }
        }

        // Get candidate skill names for implication check
        List<String> candidateSkillNames = resume.getSkills() != null
                ? resume.getSkills().stream().map(Skill::getName).toList()
                : new ArrayList<>();

        // Calculate coverage using skill implication
        for (String required : requiredSkills) {
            maxPoints += basePoint;

            boolean matched = candidateSkills.contains(required);
            if (matched) {
                totalPoints += basePoint;
                items.add(ScoreDetail.ScoreItem.builder()
                        .name(required)
                        .status("matched")
                        .points(basePoint)
                        .maxPoints(basePoint)
                        .reason("具备此技能")
                        .build());
            } else {
                // Use skill implication service for inference
                SkillImplicationService.ImplicationResult implication =
                        implicationService.checkImplication(candidateSkillNames, required);

                if (implication.isMatched()) {
                    // Inferred match - give full or partial points based on match type
                    int inferredPoints = implication.getMatchType() == SkillImplicationService.MatchType.DIRECT
                            ? basePoint
                            : (int) (basePoint * inferenceMultiplier);
                    totalPoints += inferredPoints;
                    items.add(ScoreDetail.ScoreItem.builder()
                            .name(required)
                            .status(implication.getMatchType() == SkillImplicationService.MatchType.DIRECT
                                    ? "matched" : "partial")
                            .points(inferredPoints)
                            .maxPoints(basePoint)
                            .reason("通过 " + String.join(", ", implication.getMatchingSkills()) + " 可推断")
                            .build());
                } else {
                    // Check for category-based partial match as fallback
                    boolean partial = hasRelatedSkill(required, candidateSkills);
                    if (partial) {
                        int partialPoints = (int) (basePoint * partialMultiplier);
                        totalPoints += partialPoints;
                        items.add(ScoreDetail.ScoreItem.builder()
                                .name(required)
                                .status("partial")
                                .points(partialPoints)
                                .maxPoints(basePoint)
                                .reason("有相关技能可迁移")
                                .build());
                    } else {
                        items.add(ScoreDetail.ScoreItem.builder()
                                .name(required)
                                .status("missing")
                                .points(0)
                                .maxPoints(basePoint)
                                .reason("缺少此技能")
                                .build());
                    }
                }
            }
        }

        int score = maxPoints > 0 ? (int) Math.round((double) totalPoints / maxPoints * 100) : 0;

        return ScoreDetail.builder()
                .dimension("skill")
                .score(score)
                .weight(config.getSoftScore().getWeights().getSkill())
                .items(items)
                .summary(String.format("技能匹配度：%d/%d 项匹配",
                        totalPoints / basePoint, maxPoints / basePoint))
                .build();
    }

    /**
     * Calculate experience matching score.
     */
    private ScoreDetail calculateExperienceScore(ResumeParsed resume, JDParsed jd) {
        List<ScoreDetail.ScoreItem> items = new ArrayList<>();
        int totalScore = 0;
        int itemCount = 0;

        // Get experience thresholds from config
        MatcherConfig.ExperienceScoreConfig expConfig = config.getSoftScore().getExperience();

        // Check industry relevance
        int industryScore = calculateIndustryRelevance(resume, jd);
        items.add(ScoreDetail.ScoreItem.builder()
                .name("行业相关度")
                .status(industryScore >= expConfig.getThresholds().getMatched() ? "matched" :
                        industryScore >= expConfig.getThresholds().getPartial() ? "partial" : "missing")
                .points(industryScore)
                .maxPoints(100)
                .reason(getIndustryReason(industryScore, expConfig))
                .build());
        totalScore += industryScore;
        itemCount++;

        // Check project similarity
        int projectScore = calculateProjectSimilarity(resume, jd);
        items.add(ScoreDetail.ScoreItem.builder()
                .name("项目相似度")
                .status(projectScore >= expConfig.getThresholds().getMatched() ? "matched" :
                        projectScore >= expConfig.getThresholds().getPartial() ? "partial" : "missing")
                .points(projectScore)
                .maxPoints(100)
                .reason(getProjectReason(projectScore, expConfig))
                .build());
        totalScore += projectScore;
        itemCount++;

        // Check experience depth
        int depthScore = calculateExperienceDepth(resume, jd);
        items.add(ScoreDetail.ScoreItem.builder()
                .name("经验深度")
                .status(depthScore >= expConfig.getThresholds().getMatched() ? "matched" :
                        depthScore >= expConfig.getThresholds().getPartial() ? "partial" : "missing")
                .points(depthScore)
                .maxPoints(100)
                .reason(getDepthReason(depthScore, expConfig))
                .build());
        totalScore += depthScore;
        itemCount++;

        int avgScore = itemCount > 0 ? totalScore / itemCount : 0;

        return ScoreDetail.builder()
                .dimension("experience")
                .score(avgScore)
                .weight(config.getSoftScore().getWeights().getExperience())
                .items(items)
                .summary(String.format("经验匹配度：综合评分 %d", avgScore))
                .build();
    }

    /**
     * Calculate bonus score.
     */
    private ScoreDetail calculateBonusScore(ResumeParsed resume, JDParsed jd) {
        List<ScoreDetail.ScoreItem> items = new ArrayList<>();
        int totalPoints = 0;
        int maxPoints = 0;

        // Get bonus scoring config
        MatcherConfig.BonusScoreConfig bonusConfig = config.getSoftScore().getBonus();
        int bonusSkillPoints = bonusConfig.getBonusSkillPoints();
        int normalSkillPoints = bonusConfig.getNormalSkillPoints();
        int uniqueStrengthMax = bonusConfig.getUniqueStrengthMax();
        int expertSkillBonus = bonusConfig.getExpertSkillBonus();

        // Check preferred skills
        for (SoftRequirement req : jd.getSoftRequirements()) {
            if ("skill".equals(req.getType())) {
                String skill = req.getSkill();
                if (skill != null) {
                    int pointValue = "bonus".equals(req.getWeight()) ? bonusSkillPoints : normalSkillPoints;
                    maxPoints += pointValue;

                    boolean matched = hasSkill(resume, skill);
                    if (matched) {
                        totalPoints += pointValue;
                        items.add(ScoreDetail.ScoreItem.builder()
                                .name(skill + " (优先)")
                                .status("matched")
                                .points(pointValue)
                                .maxPoints(pointValue)
                                .reason("具备加分项技能")
                                .build());
                    } else {
                        items.add(ScoreDetail.ScoreItem.builder()
                                .name(skill + " (优先)")
                                .status("missing")
                                .points(0)
                                .maxPoints(pointValue)
                                .reason("未具备此加分项")
                                .build());
                    }
                }
            }
        }

        // Check for unique strengths
        int uniqueBonus = calculateUniqueStrengths(resume, jd, expertSkillBonus, uniqueStrengthMax);
        if (uniqueBonus > 0) {
            maxPoints += uniqueStrengthMax;
            totalPoints += uniqueBonus;
            items.add(ScoreDetail.ScoreItem.builder()
                    .name("差异化优势")
                    .status("matched")
                    .points(uniqueBonus)
                    .maxPoints(uniqueStrengthMax)
                    .reason("具有独特亮点")
                    .build());
        }

        int score = maxPoints > 0 ? (int) Math.round((double) totalPoints / maxPoints * 100) : 50;

        return ScoreDetail.builder()
                .dimension("bonus")
                .score(score)
                .weight(config.getSoftScore().getWeights().getBonus())
                .items(items)
                .summary(String.format("加分项：命中 %d 项", items.stream()
                        .filter(i -> "matched".equals(i.getStatus())).count()))
                .build();
    }

    // Helper methods

    private boolean hasRelatedSkill(String required, Set<String> candidateSkills) {
        // Check if candidate has skills in the same category
        String category = skillService.lookup(required).getCategory();
        if (category != null) {
            for (String candidate : candidateSkills) {
                String candidateCategory = skillService.lookup(candidate).getCategory();
                if (category.equals(candidateCategory)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasSkill(ResumeParsed resume, String skillName) {
        if (resume.getSkills() == null) return false;

        // Direct match check
        String standardized = skillService.standardize(skillName).toLowerCase();
        for (Skill skill : resume.getSkills()) {
            String candidateStandard = skill.getStandardName() != null ?
                    skill.getStandardName() : skillService.standardize(skill.getName());
            if (standardized.equalsIgnoreCase(candidateStandard)) {
                return true;
            }
        }

        // Use skill implication for inference
        List<String> candidateSkillNames = resume.getSkills().stream()
                .map(Skill::getName)
                .toList();
        SkillImplicationService.ImplicationResult implication =
                implicationService.checkImplication(candidateSkillNames, skillName);
        return implication.isMatched();
    }

    private int calculateIndustryRelevance(ResumeParsed resume, JDParsed jd) {
        String jdText = jd.getOriginalText();
        String resumeText = resume.getOriginalText();

        if (jdText == null || resumeText == null) return 50;

        // Use industry keywords from config
        List<String> industryKeywords = config.getSoftScore().getIndustryKeywords();
        int matches = 0;
        for (String keyword : industryKeywords) {
            if (jdText.contains(keyword) && resumeText.contains(keyword)) {
                matches++;
            }
        }

        return Math.min(100, 50 + matches * 15);
    }

    private int calculateProjectSimilarity(ResumeParsed resume, JDParsed jd) {
        if (resume.getProjects() == null || resume.getProjects().isEmpty()) {
            return 30;  // No projects to evaluate
        }

        String jdText = jd.getOriginalText();
        if (jdText == null) return 50;

        int relevantProjects = 0;
        for (Project project : resume.getProjects()) {
            // Check achievements
            if (project.getAchievements() != null) {
                String achievementsText = String.join(" ", project.getAchievements());
                if (hasKeywordOverlap(achievementsText, jdText)) {
                    relevantProjects++;
                    continue;
                }
            }
            // Check tech stack
            if (project.getTechStack() != null) {
                String techText = String.join(" ", project.getTechStack());
                if (hasKeywordOverlap(techText, jdText)) {
                    relevantProjects++;
                }
            }
        }

        return Math.min(100, 30 + relevantProjects * 20);
    }

    private int calculateExperienceDepth(ResumeParsed resume, JDParsed jd) {
        if (resume.getExperiences() == null || resume.getExperiences().isEmpty()) {
            return 20;
        }

        // Score based on experience count and highlights
        int expCount = resume.getExperiences().size();
        int baseScore = Math.min(60, 20 + expCount * 10);

        // Add points for detailed highlights
        int detailBonus = 0;
        for (Experience exp : resume.getExperiences()) {
            if (exp.getHighlights() != null && !exp.getHighlights().isEmpty()) {
                detailBonus += Math.min(15, exp.getHighlights().size() * 5);
            }
        }

        return Math.min(100, baseScore + detailBonus);
    }

    private int calculateUniqueStrengths(ResumeParsed resume, JDParsed jd,
                                         int expertSkillBonus, int maxBonus) {
        int bonus = 0;
        if (resume.getSkills() != null) {
            for (Skill skill : resume.getSkills()) {
                // Skills marked as expert level get bonus
                if ("expert".equalsIgnoreCase(skill.getLevel()) ||
                        "精通".equals(skill.getLevel())) {
                    bonus += expertSkillBonus;
                }
            }
        }
        return Math.min(maxBonus, bonus);
    }

    private boolean hasKeywordOverlap(String text1, String text2) {
        if (text1 == null || text2 == null) return false;

        // Use project keywords from config
        List<String> projectKeywords = config.getSoftScore().getProjectKeywords();
        for (String keyword : projectKeywords) {
            if (text1.contains(keyword) && text2.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String getIndustryReason(int score, MatcherConfig.ExperienceScoreConfig expConfig) {
        if (score >= expConfig.getThresholds().getMatched()) return "行业背景高度相关";
        if (score >= expConfig.getThresholds().getPartial()) return "有一定行业相关性";
        return "行业背景相关度较低";
    }

    private String getProjectReason(int score, MatcherConfig.ExperienceScoreConfig expConfig) {
        if (score >= expConfig.getThresholds().getMatched()) return "项目经验与岗位高度匹配";
        if (score >= expConfig.getThresholds().getPartial()) return "有部分相关项目经验";
        return "项目经验相关度较低";
    }

    private String getDepthReason(int score, MatcherConfig.ExperienceScoreConfig expConfig) {
        if (score >= expConfig.getThresholds().getMatched()) return "经验深度充足";
        if (score >= expConfig.getThresholds().getPartial()) return "经验深度一般";
        return "经验深度不足";
    }
}
