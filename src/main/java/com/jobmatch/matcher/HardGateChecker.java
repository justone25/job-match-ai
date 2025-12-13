package com.jobmatch.matcher;

import com.jobmatch.config.MatcherConfig;
import com.jobmatch.config.MatcherConfigLoader;
import com.jobmatch.model.jd.HardRequirement;
import com.jobmatch.model.jd.JDParsed;
import com.jobmatch.model.match.*;
import com.jobmatch.model.resume.Experience;
import com.jobmatch.model.resume.ResumeParsed;
import com.jobmatch.model.resume.Skill;
import com.jobmatch.skill.SkillDictionaryService;
import com.jobmatch.skill.SkillImplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks hard gate requirements from JD against resume.
 * Uses configuration from matcher.yaml for thresholds and mappings.
 * Based on PRD v3.2 section 13.2.1.
 */
public class HardGateChecker {

    private static final Logger log = LoggerFactory.getLogger(HardGateChecker.class);
    private static final Pattern YEARS_PATTERN = Pattern.compile("(\\d+)\\s*[年+]");

    private final SkillDictionaryService skillService;
    private final SkillImplicationService implicationService;
    private final MatcherConfig config;

    public HardGateChecker() {
        this(SkillDictionaryService.getInstance(), new SkillImplicationService(),
                MatcherConfigLoader.getInstance().getConfig());
    }

    public HardGateChecker(SkillDictionaryService skillService) {
        this(skillService, new SkillImplicationService(),
                MatcherConfigLoader.getInstance().getConfig());
    }

    public HardGateChecker(SkillImplicationService implicationService) {
        this(SkillDictionaryService.getInstance(), implicationService,
                MatcherConfigLoader.getInstance().getConfig());
    }

    /**
     * Full constructor for testing.
     */
    public HardGateChecker(SkillDictionaryService skillService,
                          SkillImplicationService implicationService,
                          MatcherConfig config) {
        this.skillService = skillService;
        this.implicationService = implicationService;
        this.config = config;
    }

    /**
     * Check all hard requirements.
     */
    public HardGateResult check(ResumeParsed resume, JDParsed jd) {
        List<HardGateItem> items = new ArrayList<>();
        List<String> borderlineWarnings = new ArrayList<>();

        // Check each hard requirement from JD
        for (HardRequirement req : jd.getHardRequirements()) {
            HardGateItem item = checkRequirement(req, resume);
            items.add(item);

            if (item.isBorderline()) {
                borderlineWarnings.add(String.format("%s: %s (候选人: %s)",
                        req.getType(), req.getRequirement(), item.getCandidateValue()));
            }
        }

        // Determine overall status
        OverallGateStatus status = determineOverallStatus(items);

        // Build summary
        String summary = buildSummary(status, items);

        log.info("Hard gate check completed: {} items, status={}, passed={}, failed={}, borderline={}, unknown={}",
                items.size(), status,
                items.stream().filter(HardGateItem::isPassed).count(),
                items.stream().filter(HardGateItem::isFailed).count(),
                items.stream().filter(HardGateItem::isBorderline).count(),
                items.stream().filter(HardGateItem::isUnknown).count());

        return HardGateResult.builder()
                .status(status)
                .items(items)
                .summary(summary)
                .borderlineWarnings(borderlineWarnings)
                .build();
    }

    /**
     * Check a single requirement.
     */
    private HardGateItem checkRequirement(HardRequirement req, ResumeParsed resume) {
        return switch (req.getType()) {
            case HardRequirement.TYPE_EXPERIENCE -> checkExperience(req, resume);
            case HardRequirement.TYPE_SKILL -> checkSkill(req, resume);
            case HardRequirement.TYPE_EDUCATION -> checkEducation(req, resume);
            case HardRequirement.TYPE_CERTIFICATION -> checkCertification(req, resume);
            case HardRequirement.TYPE_INDUSTRY -> checkIndustry(req, resume);
            default -> createUnknownItem(req, "Unknown requirement type");
        };
    }

    /**
     * Check experience years requirement.
     */
    private HardGateItem checkExperience(HardRequirement req, ResumeParsed resume) {
        Integer requiredYears = req.getYearsRequired();
        if (requiredYears == null) {
            requiredYears = extractYears(req.getRequirement());
        }

        if (requiredYears == null) {
            return createUnknownItem(req, "Cannot parse required years");
        }

        // Calculate candidate's experience years
        int candidateYears = calculateTotalExperienceYears(resume);

        // Get borderline delta from config
        int borderlineDelta = config.getHardGate().getBorderline().getDeltaYears();

        HardGateStatus status;
        String explanation;

        if (candidateYears >= requiredYears) {
            status = HardGateStatus.PASS;
            explanation = String.format("候选人有 %d 年经验，满足 %d 年要求", candidateYears, requiredYears);
        } else if (candidateYears >= requiredYears - borderlineDelta) {
            status = HardGateStatus.BORDERLINE;
            explanation = String.format("候选人有 %d 年经验，接近 %d 年要求", candidateYears, requiredYears);
        } else {
            status = HardGateStatus.FAIL;
            explanation = String.format("候选人有 %d 年经验，不满足 %d 年要求", candidateYears, requiredYears);
        }

        return HardGateItem.builder()
                .requirement(req.getRequirement())
                .type(req.getType())
                .status(status)
                .candidateValue(candidateYears + "年")
                .requiredValue(requiredYears + "年")
                .evidence(HardGateItem.Evidence.builder()
                        .jdEvidence(req.getEvidence() != null && !req.getEvidence().isEmpty() ?
                                req.getEvidence().get(0) : req.getRequirement())
                        .resumeEvidence(getExperienceEvidence(resume))
                        .build())
                .explanation(explanation)
                .confidence(config.getHardGate().getConfidence().getDirectMatch())
                .build();
    }

    /**
     * Check skill requirement.
     * Uses SkillImplicationService to infer skill possession from related skills.
     */
    private HardGateItem checkSkill(HardRequirement req, ResumeParsed resume) {
        String requiredSkill = req.getSkill();
        String standardSkill = req.getStandardName() != null ?
                req.getStandardName() : skillService.standardize(requiredSkill);

        // Find matching skill in resume (direct match)
        Skill matchedSkill = findMatchingSkill(resume, standardSkill);

        HardGateStatus status;
        String candidateValue;
        String explanation;
        String resumeEvidence = null;
        double confidence = config.getHardGate().getConfidence().getDirectMatch();

        if (matchedSkill != null) {
            // Direct skill match found
            candidateValue = matchedSkill.getName() +
                    (matchedSkill.getLevel() != null ? " (" + matchedSkill.getLevel() + ")" : "");
            resumeEvidence = matchedSkill.getEvidence() != null && !matchedSkill.getEvidence().isEmpty()
                    ? matchedSkill.getEvidence().get(0) : null;

            // Check proficiency level if required
            if (req.getLevel() != null && matchedSkill.getLevel() != null) {
                int comparison = compareProficiency(matchedSkill.getLevel(), req.getLevel());
                if (comparison >= 0) {
                    status = HardGateStatus.PASS;
                    explanation = String.format("候选人具备 %s 技能，熟练度满足要求", standardSkill);
                } else {
                    status = HardGateStatus.BORDERLINE;
                    explanation = String.format("候选人具备 %s 技能，但熟练度(%s)低于要求(%s)",
                            standardSkill, matchedSkill.getLevel(), req.getLevel());
                }
            } else {
                status = HardGateStatus.PASS;
                explanation = String.format("候选人具备 %s 技能", standardSkill);
            }
        } else {
            // No direct match - try skill implication reasoning
            List<String> candidateSkillNames = resume.getSkills() != null
                    ? resume.getSkills().stream().map(Skill::getName).collect(Collectors.toList())
                    : new ArrayList<>();

            SkillImplicationService.ImplicationResult implicationResult =
                    implicationService.checkImplication(candidateSkillNames, requiredSkill);

            if (implicationResult.isMatched()) {
                // Skill can be inferred from other skills
                candidateValue = implicationResult.getEvidence();
                status = HardGateStatus.PASS;
                explanation = String.format("候选人通过 %s 可推断具备 %s 能力",
                        String.join(", ", implicationResult.getMatchingSkills()), standardSkill);
                resumeEvidence = "技能推断: " + String.join(", ", implicationResult.getMatchingSkills());

                // Use confidence from implication result or config
                confidence = implicationResult.getConfidence() > 0
                        ? implicationResult.getConfidence()
                        : config.getHardGate().getConfidence().getImpliedMatch();

                log.debug("Skill implication matched: {} -> {} via {} (type={}, confidence={})",
                        requiredSkill, standardSkill, implicationResult.getMatchingSkills(),
                        implicationResult.getMatchType(), confidence);
            } else {
                status = HardGateStatus.FAIL;
                candidateValue = "未找到";
                explanation = String.format("简历中未找到 %s 相关技能", standardSkill);
                confidence = config.getHardGate().getConfidence().getUnknown();
            }
        }

        return HardGateItem.builder()
                .requirement(req.getRequirement())
                .type(req.getType())
                .status(status)
                .candidateValue(candidateValue)
                .requiredValue(requiredSkill + (req.getLevel() != null ? " (" + req.getLevel() + ")" : ""))
                .evidence(HardGateItem.Evidence.builder()
                        .jdEvidence(req.getEvidence() != null && !req.getEvidence().isEmpty() ?
                                req.getEvidence().get(0) : req.getRequirement())
                        .resumeEvidence(resumeEvidence)
                        .build())
                .explanation(explanation)
                .confidence(confidence)
                .build();
    }

    /**
     * Check education requirement.
     */
    private HardGateItem checkEducation(HardRequirement req, ResumeParsed resume) {
        String candidateEducation = resume.getBasicInfo() != null ?
                resume.getBasicInfo().getEducation() : null;

        if (candidateEducation == null || candidateEducation.isEmpty()) {
            return createUnknownItem(req, "简历中未找到学历信息");
        }

        // Use config for education level mapping
        int requiredLevel = config.getEducationScore(req.getRequirement());
        int candidateLevel = config.getEducationScore(candidateEducation);

        HardGateStatus status;
        String explanation;

        if (candidateLevel >= requiredLevel) {
            status = HardGateStatus.PASS;
            explanation = String.format("候选人学历(%s)满足要求", candidateEducation);
        } else if (candidateLevel == requiredLevel - 1) {
            status = HardGateStatus.BORDERLINE;
            explanation = String.format("候选人学历(%s)略低于要求", candidateEducation);
        } else {
            status = HardGateStatus.FAIL;
            explanation = String.format("候选人学历(%s)不满足要求", candidateEducation);
        }

        return HardGateItem.builder()
                .requirement(req.getRequirement())
                .type(req.getType())
                .status(status)
                .candidateValue(candidateEducation)
                .requiredValue(req.getRequirement())
                .evidence(HardGateItem.Evidence.builder()
                        .jdEvidence(req.getEvidence() != null && !req.getEvidence().isEmpty() ?
                                req.getEvidence().get(0) : req.getRequirement())
                        .resumeEvidence(candidateEducation)
                        .build())
                .explanation(explanation)
                .confidence(config.getHardGate().getConfidence().getDirectMatch() * 0.9)
                .build();
    }

    /**
     * Check certification requirement.
     */
    private HardGateItem checkCertification(HardRequirement req, ResumeParsed resume) {
        String resumeText = resume.getOriginalText();
        if (resumeText == null) {
            return createUnknownItem(req, "无法检查证书信息");
        }

        String certName = req.getRequirement();
        boolean found = resumeText.toLowerCase().contains(certName.toLowerCase());

        return HardGateItem.builder()
                .requirement(req.getRequirement())
                .type(req.getType())
                .status(found ? HardGateStatus.PASS : HardGateStatus.UNKNOWN)
                .candidateValue(found ? "已找到" : "未找到")
                .requiredValue(certName)
                .explanation(found ? "简历中提及相关证书" : "简历中未明确提及此证书")
                .confidence(found ? config.getHardGate().getConfidence().getKeywordMatch()
                        : config.getHardGate().getConfidence().getUnknown())
                .build();
    }

    /**
     * Check industry requirement.
     */
    private HardGateItem checkIndustry(HardRequirement req, ResumeParsed resume) {
        String industry = req.getRequirement();
        String resumeText = resume.getOriginalText();

        if (resumeText == null) {
            return createUnknownItem(req, "无法检查行业经验");
        }

        boolean found = resumeText.toLowerCase().contains(industry.toLowerCase());

        // Also check experience companies
        if (!found && resume.getExperiences() != null) {
            for (Experience exp : resume.getExperiences()) {
                if (exp.getCompany() != null &&
                        exp.getCompany().toLowerCase().contains(industry.toLowerCase())) {
                    found = true;
                    break;
                }
            }
        }

        return HardGateItem.builder()
                .requirement(req.getRequirement())
                .type(req.getType())
                .status(found ? HardGateStatus.PASS : HardGateStatus.UNKNOWN)
                .candidateValue(found ? "有相关经验" : "未明确")
                .requiredValue(industry)
                .explanation(found ? "候选人有相关行业经验" : "无法确定是否有相关行业经验")
                .confidence(found ? config.getHardGate().getConfidence().getKeywordMatch()
                        : config.getHardGate().getConfidence().getUnknown())
                .build();
    }

    // Helper methods

    private HardGateItem createUnknownItem(HardRequirement req, String reason) {
        return HardGateItem.builder()
                .requirement(req.getRequirement())
                .type(req.getType())
                .status(HardGateStatus.UNKNOWN)
                .candidateValue("信息不足")
                .requiredValue(req.getValue())
                .explanation(reason)
                .confidence(config.getHardGate().getConfidence().getUnknown())
                .build();
    }

    private Integer extractYears(String text) {
        if (text == null) return null;
        Matcher matcher = YEARS_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private int calculateTotalExperienceYears(ResumeParsed resume) {
        // Try to get from basic info first
        if (resume.getBasicInfo() != null && resume.getBasicInfo().getExperienceYears() != null) {
            return resume.getBasicInfo().getExperienceYears();
        }

        // Calculate from experiences - parse duration strings
        if (resume.getExperiences() == null || resume.getExperiences().isEmpty()) {
            return 0;
        }

        int totalMonths = 0;
        for (Experience exp : resume.getExperiences()) {
            totalMonths += parseDurationMonths(exp.getDuration());
        }

        return totalMonths / 12;
    }

    /**
     * Parse duration string to months.
     * Expected format: "2019.03-2021.06" or "2020.01-至今"
     */
    private int parseDurationMonths(String duration) {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }
        // Try to extract years from duration
        java.util.regex.Matcher yearMatcher = Pattern.compile("(\\d{4})").matcher(duration);
        java.util.List<Integer> years = new java.util.ArrayList<>();
        while (yearMatcher.find()) {
            years.add(Integer.parseInt(yearMatcher.group(1)));
        }
        if (years.size() >= 2) {
            return (years.get(1) - years.get(0)) * 12;
        } else if (years.size() == 1 && (duration.contains("至今") || duration.contains("present"))) {
            return (java.time.Year.now().getValue() - years.get(0)) * 12;
        }
        // Use default from config
        return config.getHardGate().getDuration().getDefaultMonths();
    }

    private String getExperienceEvidence(ResumeParsed resume) {
        if (resume.getExperiences() == null || resume.getExperiences().isEmpty()) {
            return null;
        }
        Experience first = resume.getExperiences().get(0);
        return String.format("%s - %s (%s)",
                first.getCompany(), first.getTitle(), first.getDuration());
    }

    private Skill findMatchingSkill(ResumeParsed resume, String standardSkill) {
        if (resume.getSkills() == null) return null;

        for (Skill skill : resume.getSkills()) {
            String skillStandard = skill.getStandardName() != null ?
                    skill.getStandardName() : skillService.standardize(skill.getName());
            if (standardSkill.equalsIgnoreCase(skillStandard)) {
                return skill;
            }
        }
        return null;
    }

    private int compareProficiency(String candidateLevel, String requiredLevel) {
        int candidateScore = config.getProficiencyScore(candidateLevel);
        int requiredScore = config.getProficiencyScore(requiredLevel);
        return Integer.compare(candidateScore, requiredScore);
    }

    private OverallGateStatus determineOverallStatus(List<HardGateItem> items) {
        boolean hasFail = items.stream().anyMatch(HardGateItem::isFailed);
        boolean hasUnknown = items.stream().anyMatch(HardGateItem::isUnknown);

        if (hasFail) {
            return OverallGateStatus.FAILED;
        }
        if (hasUnknown) {
            return OverallGateStatus.UNCERTAIN;
        }
        return OverallGateStatus.PASSED;
    }

    private String buildSummary(OverallGateStatus status, List<HardGateItem> items) {
        int passed = (int) items.stream().filter(HardGateItem::isPassed).count();
        int failed = (int) items.stream().filter(HardGateItem::isFailed).count();
        int borderline = (int) items.stream().filter(HardGateItem::isBorderline).count();
        int unknown = (int) items.stream().filter(HardGateItem::isUnknown).count();

        return switch (status) {
            case PASSED -> String.format("硬性门槛检查通过：%d项通过%s",
                    passed, borderline > 0 ? "，" + borderline + "项边界情况需关注" : "");
            case FAILED -> String.format("硬性门槛检查未通过：%d项不满足要求", failed);
            case UNCERTAIN -> String.format("硬性门槛信息不足：%d项无法判断，建议补充简历信息", unknown);
        };
    }
}
