package com.jobmatch.parser;

import com.jobmatch.model.common.ParseMeta;
import com.jobmatch.model.jd.HardRequirement;
import com.jobmatch.model.jd.ImplicitRequirement;
import com.jobmatch.model.jd.JDParsed;
import com.jobmatch.model.jd.SoftRequirement;
import com.jobmatch.model.resume.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rule-based parser as fallback when LLM is unavailable.
 * Provides basic extraction using keyword matching and regex patterns.
 *
 * Capabilities:
 * - Keyword matching for skills
 * - Regex extraction for years of experience
 * - Simple coverage calculation
 *
 * Limitations:
 * - No semantic understanding
 * - Cannot identify implicit requirements
 * - No personalized suggestions
 */
public class RuleBasedParser {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedParser.class);

    // Regex patterns for experience extraction
    private static final Pattern YEARS_PATTERN = Pattern.compile(
            "(\\d+)\\s*[+\\-~～至到]?\\s*(\\d+)?\\s*年(?:以上|及以上)?(?:工作)?经[验历]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern YEARS_PATTERN_EN = Pattern.compile(
            "(\\d+)\\s*[+\\-~]?\\s*(\\d+)?\\s*(?:years?|yrs?)\\s*(?:of)?\\s*(?:experience)?",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EDUCATION_PATTERN = Pattern.compile(
            "(本科|硕士|博士|大专|学士|研究生|Bachelor|Master|PhD|MBA|BS|MS)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d{4})[./-](\\d{1,2})\\s*[-~至到]\\s*(\\d{4}|至今|present)[./-]?(\\d{1,2})?",
            Pattern.CASE_INSENSITIVE
    );

    public RuleBasedParser() {
        // No-op, all resources are static or created on-demand
    }

    /**
     * Parse resume using rule-based extraction.
     * Returns result with low confidence indicators.
     */
    public ResumeParsed parseResume(String text) {
        log.info("Parsing resume with rule-based engine (fallback mode)");
        long startTime = System.currentTimeMillis();

        // Extract skills by matching against dictionary
        List<Skill> skills = extractSkills(text);

        // Extract years of experience
        Integer yearsExp = extractYearsOfExperience(text);

        // Extract education
        String education = extractEducation(text);

        // Extract basic work experiences
        List<Experience> experiences = extractExperiences(text);

        ResumeBasicInfo basicInfo = ResumeBasicInfo.builder()
                .experienceYears(yearsExp)
                .education(education)
                .evidence(List.of("Rule-based extraction"))
                .build();

        long latencyMs = System.currentTimeMillis() - startTime;

        ResumeParsed result = ResumeParsed.builder()
                .basicInfo(basicInfo)
                .skills(skills)
                .experiences(experiences)
                .projects(new ArrayList<>())
                .originalText(text)
                .parseMeta(ParseMeta.builder()
                        .parseTime(Instant.now())
                        .modelVersion("rule-engine-v1")
                        .llmProvider("rule-engine")
                        .latencyMs(latencyMs)
                        .overallConfidence(0.5) // Low confidence for rule-based
                        .fromCache(false)
                        .build())
                .build();

        log.info("Rule-based resume parsing complete: {} skills, {} experiences, latency={}ms",
                skills.size(), experiences.size(), latencyMs);

        return result;
    }

    /**
     * Parse JD using rule-based extraction.
     */
    public JDParsed parseJD(String text) {
        log.info("Parsing JD with rule-based engine (fallback mode)");
        long startTime = System.currentTimeMillis();

        // Extract skills as hard requirements
        List<Skill> skills = extractSkills(text);

        // Convert skills to hard requirements
        List<HardRequirement> hardReqs = skills.stream()
                .limit(10) // Limit to most prominent skills
                .map(skill -> HardRequirement.builder()
                        .type(HardRequirement.TYPE_SKILL)
                        .skill(skill.getName())
                        .requirement(skill.getName() + " 技能要求")
                        .evidence(List.of("Rule-based extraction: " + skill.getName()))
                        .confidence(0.5)
                        .build())
                .collect(Collectors.toList());

        // Extract years requirement
        Integer yearsReq = extractYearsOfExperience(text);
        if (yearsReq != null) {
            hardReqs.add(HardRequirement.builder()
                    .type(HardRequirement.TYPE_EXPERIENCE)
                    .requirement(yearsReq + "年以上工作经验")
                    .value(yearsReq + "年+")
                    .yearsRequired(yearsReq)
                    .evidence(List.of("Rule-based extraction"))
                    .confidence(0.6)
                    .build());
        }

        // Extract education requirement
        String eduReq = extractEducation(text);
        if (eduReq != null) {
            hardReqs.add(HardRequirement.builder()
                    .type(HardRequirement.TYPE_EDUCATION)
                    .requirement(eduReq + "及以上学历")
                    .value(eduReq)
                    .evidence(List.of("Rule-based extraction"))
                    .confidence(0.6)
                    .build());
        }

        // Create placeholder soft requirements (cannot extract using rule engine)
        List<SoftRequirement> softReqs = new ArrayList<>();

        long latencyMs = System.currentTimeMillis() - startTime;

        JDParsed result = JDParsed.builder()
                .hardRequirements(hardReqs)
                .softRequirements(softReqs)
                .implicitRequirements(new ArrayList<>())
                .originalText(text)
                .parseMeta(ParseMeta.builder()
                        .parseTime(Instant.now())
                        .modelVersion("rule-engine-v1")
                        .llmProvider("rule-engine")
                        .latencyMs(latencyMs)
                        .overallConfidence(0.5)
                        .fromCache(false)
                        .build())
                .build();

        log.info("Rule-based JD parsing complete: {} hard requirements, latency={}ms",
                hardReqs.size(), latencyMs);

        return result;
    }

    /**
     * Extract skills by matching against known skill patterns.
     */
    private List<Skill> extractSkills(String text) {
        Set<String> foundSkills = new LinkedHashSet<>();
        String lowerText = text.toLowerCase();

        // Check for common skills
        String[] commonSkills = {
                "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust", "C++", "C#",
                "Spring", "Spring Boot", "Spring Cloud", "MyBatis", "Hibernate",
                "MySQL", "PostgreSQL", "MongoDB", "Redis", "Elasticsearch",
                "Docker", "Kubernetes", "K8s", "AWS", "Azure", "GCP",
                "Git", "Linux", "CI/CD", "Jenkins", "Maven", "Gradle",
                "React", "Vue", "Angular", "Node.js", "Express",
                "Kafka", "RabbitMQ", "Nginx", "Tomcat",
                "REST", "GraphQL", "gRPC", "WebSocket",
                "JUnit", "Mockito", "Selenium", "JMeter"
        };

        for (String skill : commonSkills) {
            if (containsSkill(lowerText, skill.toLowerCase()) && !foundSkills.contains(skill)) {
                foundSkills.add(skill);
            }
        }

        return foundSkills.stream()
                .map(name -> Skill.builder()
                        .name(name)
                        .confidence(0.5)
                        .evidence(List.of("Keyword match in text"))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Check if text contains skill (word boundary aware).
     */
    private boolean containsSkill(String text, String skill) {
        // Handle special characters in skill names
        String escapedSkill = Pattern.quote(skill);
        Pattern pattern = Pattern.compile("\\b" + escapedSkill + "\\b|" +
                escapedSkill + "[\\s,;.。，；]|[\\s,;.。，；]" + escapedSkill,
                Pattern.CASE_INSENSITIVE);
        return pattern.matcher(text).find();
    }

    /**
     * Extract years of experience from text.
     */
    private Integer extractYearsOfExperience(String text) {
        // Try Chinese pattern first
        Matcher matcher = YEARS_PATTERN.matcher(text);
        if (matcher.find()) {
            int min = Integer.parseInt(matcher.group(1));
            String max = matcher.group(2);
            return max != null ? (min + Integer.parseInt(max)) / 2 : min;
        }

        // Try English pattern
        matcher = YEARS_PATTERN_EN.matcher(text);
        if (matcher.find()) {
            int min = Integer.parseInt(matcher.group(1));
            String max = matcher.group(2);
            return max != null ? (min + Integer.parseInt(max)) / 2 : min;
        }

        return null;
    }

    /**
     * Extract education level from text.
     */
    private String extractEducation(String text) {
        Matcher matcher = EDUCATION_PATTERN.matcher(text);
        if (matcher.find()) {
            String edu = matcher.group(1);
            return normalizeEducation(edu);
        }
        return null;
    }

    /**
     * Normalize education level to standard format.
     */
    private String normalizeEducation(String edu) {
        String lower = edu.toLowerCase();
        if (lower.contains("博士") || lower.contains("phd")) {
            return "博士";
        } else if (lower.contains("硕士") || lower.contains("master") || lower.contains("研究生") || lower.equals("ms")) {
            return "硕士";
        } else if (lower.contains("本科") || lower.contains("bachelor") || lower.contains("学士") || lower.equals("bs")) {
            return "本科";
        } else if (lower.contains("大专")) {
            return "大专";
        } else if (lower.equals("mba")) {
            return "MBA";
        }
        return edu;
    }

    /**
     * Extract work experiences from text.
     * Simple extraction based on duration patterns.
     */
    private List<Experience> extractExperiences(String text) {
        List<Experience> experiences = new ArrayList<>();

        Matcher matcher = DURATION_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find() && count < 5) {
            String startYear = matcher.group(1);
            String endYear = matcher.group(3);
            String duration = startYear + "-" + endYear;

            experiences.add(Experience.builder()
                    .duration(duration)
                    .confidence(0.4)
                    .evidence(List.of("Duration pattern match"))
                    .build());
            count++;
        }

        return experiences;
    }

    /**
     * Check if parser can handle this content.
     * Rule-based parser can always attempt parsing.
     */
    public boolean canParse(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
