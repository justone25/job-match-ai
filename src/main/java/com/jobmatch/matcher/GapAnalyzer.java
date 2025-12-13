package com.jobmatch.matcher;

import com.jobmatch.config.MatcherConfig;
import com.jobmatch.config.MatcherConfigLoader;
import com.jobmatch.model.jd.HardRequirement;
import com.jobmatch.model.jd.JDParsed;
import com.jobmatch.model.jd.SoftRequirement;
import com.jobmatch.model.match.GapAnalysis;
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
import java.util.stream.Collectors;

/**
 * Analyzes gaps between resume and JD requirements.
 * Uses configuration from matcher.yaml for templates and categories.
 * Based on PRD v3.2 section 13.1.3.
 */
public class GapAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GapAnalyzer.class);

    private final SkillDictionaryService skillService;
    private final SkillImplicationService implicationService;
    private final MatcherConfig config;

    public GapAnalyzer() {
        this(SkillDictionaryService.getInstance(), new SkillImplicationService(),
                MatcherConfigLoader.getInstance().getConfig());
    }

    public GapAnalyzer(SkillDictionaryService skillService) {
        this(skillService, new SkillImplicationService(),
                MatcherConfigLoader.getInstance().getConfig());
    }

    public GapAnalyzer(SkillImplicationService implicationService) {
        this(SkillDictionaryService.getInstance(), implicationService,
                MatcherConfigLoader.getInstance().getConfig());
    }

    /**
     * Full constructor for testing.
     */
    public GapAnalyzer(SkillDictionaryService skillService,
                       SkillImplicationService implicationService,
                       MatcherConfig config) {
        this.skillService = skillService;
        this.implicationService = implicationService;
        this.config = config;
    }

    /**
     * Analyze gaps between resume and JD.
     */
    public GapAnalysis analyze(ResumeParsed resume, JDParsed jd) {
        List<GapAnalysis.GapItem> missing = new ArrayList<>();
        List<GapAnalysis.GapItem> insufficient = new ArrayList<>();
        List<GapAnalysis.StrengthItem> strengths;

        // Get candidate skills set
        Set<String> candidateSkills = getCandidateSkills(resume);

        // Get candidate skill names for implication checking
        List<String> candidateSkillNames = resume.getSkills() != null
                ? resume.getSkills().stream().map(Skill::getName).collect(Collectors.toList())
                : new ArrayList<>();

        // Analyze hard requirements
        int priority = 1;
        for (HardRequirement req : jd.getHardRequirements()) {
            if (HardRequirement.TYPE_SKILL.equals(req.getType())) {
                String skill = req.getStandardName() != null ? req.getStandardName() :
                        skillService.standardize(req.getSkill());

                if (!candidateSkills.contains(skill.toLowerCase())) {
                    // Try skill implication check (same as HardGateChecker)
                    SkillImplicationService.ImplicationResult implicationResult =
                            implicationService.checkImplication(candidateSkillNames, req.getSkill());

                    if (implicationResult.isMatched()) {
                        // Skill can be inferred - not a gap
                        log.debug("Skill {} can be inferred from {}, not a gap",
                                skill, implicationResult.getMatchingSkills());
                        continue;
                    }

                    // Check if it's a proficiency gap or complete miss
                    Skill partialMatch = findPartialMatch(resume, skill);
                    if (partialMatch != null) {
                        insufficient.add(createInsufficientGap(req, partialMatch, priority++));
                    } else {
                        missing.add(createMissingGap(req, priority++));
                    }
                }
            }
        }

        // Analyze soft requirements
        for (SoftRequirement req : jd.getSoftRequirements()) {
            if ("skill".equals(req.getType()) && req.getSkill() != null) {
                String skill = skillService.standardize(req.getSkill());
                if (!candidateSkills.contains(skill.toLowerCase())) {
                    // Try skill implication check
                    SkillImplicationService.ImplicationResult implicationResult =
                            implicationService.checkImplication(candidateSkillNames, req.getSkill());

                    if (implicationResult.isMatched()) {
                        // Skill can be inferred - not a gap
                        log.debug("Soft skill {} can be inferred from {}, not a gap",
                                skill, implicationResult.getMatchingSkills());
                        continue;
                    }

                    missing.add(GapAnalysis.GapItem.builder()
                            .name(skill)
                            .type("preferred_skill")
                            .requiredLevel("优先")
                            .impact(config.getGapAnalyzer().getImpactLevels().get("low").getLabel())
                            .jdEvidence(req.getEvidence() != null && !req.getEvidence().isEmpty() ?
                                    req.getEvidence().get(0) : null)
                            .suggestion("学习 " + skill + " 可提升竞争力")
                            .priority(priority++)
                            .build());
                }
            }
        }

        // Find strengths
        strengths = findStrengths(resume, jd, candidateSkills);

        log.info("Gap analysis completed: {} missing, {} insufficient, {} strengths",
                missing.size(), insufficient.size(), strengths.size());

        return GapAnalysis.builder()
                .missing(missing)
                .insufficient(insufficient)
                .strengths(strengths)
                .build();
    }

    private Set<String> getCandidateSkills(ResumeParsed resume) {
        Set<String> skills = new HashSet<>();
        if (resume.getSkills() != null) {
            for (Skill skill : resume.getSkills()) {
                String standard = skill.getStandardName() != null ? skill.getStandardName() :
                        skillService.standardize(skill.getName());
                skills.add(standard.toLowerCase());
            }
        }
        return skills;
    }

    private Skill findPartialMatch(ResumeParsed resume, String targetSkill) {
        if (resume.getSkills() == null) return null;

        // Look for skills in the same category
        String targetCategory = skillService.lookup(targetSkill).getCategory();
        if (targetCategory == null) return null;

        for (Skill skill : resume.getSkills()) {
            String standard = skill.getStandardName() != null ? skill.getStandardName() :
                    skillService.standardize(skill.getName());
            String category = skillService.lookup(standard).getCategory();
            if (targetCategory.equals(category)) {
                return skill;
            }
        }
        return null;
    }

    private GapAnalysis.GapItem createMissingGap(HardRequirement req, int priority) {
        String skill = req.getStandardName() != null ? req.getStandardName() : req.getSkill();
        return GapAnalysis.GapItem.builder()
                .name(skill)
                .type("skill")
                .requiredLevel(req.getLevel() != null ? req.getLevel() : "必备")
                .impact(config.getGapAnalyzer().getImpactLevels().get("high").getLabel())
                .jdEvidence(req.getEvidence() != null && !req.getEvidence().isEmpty() ?
                        req.getEvidence().get(0) : req.getRequirement())
                .suggestion(generateSkillSuggestion(skill))
                .priority(priority)
                .build();
    }

    private GapAnalysis.GapItem createInsufficientGap(HardRequirement req, Skill partialMatch, int priority) {
        String skill = req.getStandardName() != null ? req.getStandardName() : req.getSkill();
        return GapAnalysis.GapItem.builder()
                .name(skill)
                .type("skill")
                .requiredLevel(req.getLevel())
                .currentLevel(partialMatch.getLevel())
                .impact(config.getGapAnalyzer().getImpactLevels().get("medium").getLabel())
                .jdEvidence(req.getEvidence() != null && !req.getEvidence().isEmpty() ?
                        req.getEvidence().get(0) : req.getRequirement())
                .suggestion(String.format("提升 %s 熟练度，从 %s 到 %s",
                        skill, partialMatch.getLevel(), req.getLevel()))
                .priority(priority)
                .build();
    }

    private List<GapAnalysis.StrengthItem> findStrengths(ResumeParsed resume, JDParsed jd,
                                                         Set<String> candidateSkills) {
        List<GapAnalysis.StrengthItem> strengths = new ArrayList<>();

        // Required skills set
        Set<String> requiredSkills = new HashSet<>();
        for (HardRequirement req : jd.getHardRequirements()) {
            if (HardRequirement.TYPE_SKILL.equals(req.getType())) {
                String skill = req.getStandardName() != null ? req.getStandardName() :
                        skillService.standardize(req.getSkill());
                requiredSkills.add(skill.toLowerCase());
            }
        }

        // Find expert-level skills
        if (resume.getSkills() != null) {
            for (Skill skill : resume.getSkills()) {
                if ("expert".equalsIgnoreCase(skill.getLevel()) || "精通".equals(skill.getLevel())) {
                    String standard = skill.getStandardName() != null ? skill.getStandardName() :
                            skillService.standardize(skill.getName());

                    // Extra value if it's a required skill
                    String relevance = requiredSkills.contains(standard.toLowerCase()) ? "high" : "medium";

                    strengths.add(GapAnalysis.StrengthItem.builder()
                            .name(standard)
                            .type("skill")
                            .description(String.format("精通 %s", standard))
                            .highlightSuggestion(String.format("在简历和面试中重点强调 %s 的深度经验", standard))
                            .evidence(skill.getEvidence() != null && !skill.getEvidence().isEmpty() ?
                                    skill.getEvidence().get(0) : null)
                            .relevance(relevance)
                            .build());
                }
            }
        }

        // Find extra skills not required but valuable (use config for categories)
        List<String> valuableCategories = config.getGapAnalyzer().getValuableCategories();
        for (String candidateSkill : candidateSkills) {
            if (!requiredSkills.contains(candidateSkill)) {
                String category = skillService.lookup(candidateSkill).getCategory();
                if (category != null && valuableCategories.contains(category)) {
                    strengths.add(GapAnalysis.StrengthItem.builder()
                            .name(candidateSkill)
                            .type("extra_skill")
                            .description("具备额外的 " + category + " 技能")
                            .highlightSuggestion("可作为差异化亮点")
                            .relevance("medium")
                            .build());
                }
            }
        }

        return strengths;
    }

    /**
     * Generate skill learning suggestion using config templates.
     */
    private String generateSkillSuggestion(String skill) {
        String category = skillService.lookup(skill).getCategory();
        return config.getSuggestionTemplate(category, skill);
    }
}
