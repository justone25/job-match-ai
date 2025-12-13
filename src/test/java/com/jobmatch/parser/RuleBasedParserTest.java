package com.jobmatch.parser;

import com.jobmatch.model.jd.HardRequirement;
import com.jobmatch.model.jd.JDParsed;
import com.jobmatch.model.resume.ResumeParsed;
import com.jobmatch.model.resume.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RuleBasedParser.
 */
class RuleBasedParserTest {

    private RuleBasedParser parser;

    @BeforeEach
    void setUp() {
        parser = new RuleBasedParser();
    }

    // ==================== Resume Parsing Tests ====================

    @Test
    @DisplayName("Should extract skills from resume text")
    void shouldExtractSkillsFromResume() {
        String resumeText = """
                技术技能：
                - 精通 Java、Spring Boot、MySQL
                - 熟悉 Docker、Kubernetes
                - 了解 React、TypeScript
                """;

        ResumeParsed result = parser.parseResume(resumeText);

        assertNotNull(result);
        assertNotNull(result.getSkills());
        assertTrue(result.getSkills().size() >= 5);

        List<String> skillNames = result.getSkills().stream()
                .map(Skill::getName)
                .toList();
        assertTrue(skillNames.contains("Java"));
        assertTrue(skillNames.contains("Spring Boot"));
        assertTrue(skillNames.contains("MySQL"));
    }

    @Test
    @DisplayName("Should extract years of experience from resume")
    void shouldExtractYearsOfExperience() {
        // Use pattern that matches the regex: "N年工作经验" or "N年经验"
        String resumeText = """
                个人简介：
                拥有8年工作经验的资深Java工程师
                """;

        ResumeParsed result = parser.parseResume(resumeText);

        assertNotNull(result);
        assertNotNull(result.getBasicInfo());
        assertEquals(8, result.getBasicInfo().getExperienceYears());
    }

    @Test
    @DisplayName("Should extract education from resume")
    void shouldExtractEducation() {
        String resumeText = """
                教育背景：
                本科 - 计算机科学与技术
                """;

        ResumeParsed result = parser.parseResume(resumeText);

        assertNotNull(result);
        assertNotNull(result.getBasicInfo());
        assertEquals("本科", result.getBasicInfo().getEducation());
    }

    @Test
    @DisplayName("Should handle empty resume text")
    void shouldHandleEmptyResume() {
        ResumeParsed result = parser.parseResume("");

        assertNotNull(result);
        assertTrue(result.getSkills().isEmpty());
    }

    @Test
    @DisplayName("Should set low confidence for rule-based parsing")
    void shouldSetLowConfidenceForResume() {
        String resumeText = "Java developer with 5 years experience";

        ResumeParsed result = parser.parseResume(resumeText);

        assertNotNull(result.getParseMeta());
        assertEquals(0.5, result.getParseMeta().getOverallConfidence());
        assertEquals("rule-engine", result.getParseMeta().getLlmProvider());
    }

    // ==================== JD Parsing Tests ====================

    @Test
    @DisplayName("Should extract hard requirements from JD")
    void shouldExtractHardRequirementsFromJD() {
        // Use patterns that match the regex patterns
        String jdText = """
                职位要求：
                - 5年以上工作经验
                - 本科学历要求
                - 精通 Spring Boot、MySQL、Redis
                """;

        JDParsed result = parser.parseJD(jdText);

        assertNotNull(result);
        assertNotNull(result.getHardRequirements());
        assertTrue(result.getHardRequirements().size() >= 2);

        // Check experience requirement
        boolean hasExpReq = result.getHardRequirements().stream()
                .anyMatch(r -> HardRequirement.TYPE_EXPERIENCE.equals(r.getType()));
        assertTrue(hasExpReq, "Should find experience requirement");

        // Check education requirement
        boolean hasEduReq = result.getHardRequirements().stream()
                .anyMatch(r -> HardRequirement.TYPE_EDUCATION.equals(r.getType()));
        assertTrue(hasEduReq, "Should find education requirement");

        // Check skill requirements
        long skillReqs = result.getHardRequirements().stream()
                .filter(r -> HardRequirement.TYPE_SKILL.equals(r.getType()))
                .count();
        assertTrue(skillReqs >= 2, "Should find at least 2 skill requirements");
    }

    @Test
    @DisplayName("Should parse years requirement pattern - Chinese")
    void shouldParseYearsPatternChinese() {
        // The pattern requires "年" followed by "经验" or "经历"
        String jdText = "需要3年以上工作经验";
        JDParsed result = parser.parseJD(jdText);

        boolean hasExpReq = result.getHardRequirements().stream()
                .anyMatch(r -> HardRequirement.TYPE_EXPERIENCE.equals(r.getType()) &&
                        r.getYearsRequired() != null &&
                        r.getYearsRequired() >= 3);
        assertTrue(hasExpReq, "Should find years requirement");
    }

    @Test
    @DisplayName("Should parse years requirement pattern - English")
    void shouldParseYearsPatternEnglish() {
        String jdText = "5+ years of experience in software development";
        JDParsed result = parser.parseJD(jdText);

        boolean hasExpReq = result.getHardRequirements().stream()
                .anyMatch(r -> HardRequirement.TYPE_EXPERIENCE.equals(r.getType()) &&
                        r.getYearsRequired() != null &&
                        r.getYearsRequired() == 5);
        assertTrue(hasExpReq);
    }

    @Test
    @DisplayName("Should handle JD with no requirements")
    void shouldHandleJDWithNoRequirements() {
        String jdText = "This is a general description with no specific requirements.";

        JDParsed result = parser.parseJD(jdText);

        assertNotNull(result);
        assertNotNull(result.getHardRequirements());
        // May have some skill matches but no experience/education
    }

    @Test
    @DisplayName("Should set low confidence for JD parsing")
    void shouldSetLowConfidenceForJD() {
        String jdText = "Java Developer position";

        JDParsed result = parser.parseJD(jdText);

        assertNotNull(result.getParseMeta());
        assertEquals(0.5, result.getParseMeta().getOverallConfidence());
        assertEquals("rule-engine", result.getParseMeta().getLlmProvider());
    }

    @Test
    @DisplayName("Should extract multiple education patterns")
    void shouldExtractMultipleEducationPatterns() {
        // Test Chinese patterns
        assertEquals("硕士", parseAndGetEducation("硕士及以上学历"));
        assertEquals("本科", parseAndGetEducation("本科学历要求"));
        assertEquals("博士", parseAndGetEducation("博士优先"));

        // Test English patterns
        assertEquals("硕士", parseAndGetEducation("Master's degree required"));
        assertEquals("本科", parseAndGetEducation("Bachelor's degree"));
    }

    private String parseAndGetEducation(String text) {
        JDParsed result = parser.parseJD(text);
        return result.getHardRequirements().stream()
                .filter(r -> HardRequirement.TYPE_EDUCATION.equals(r.getType()))
                .findFirst()
                .map(HardRequirement::getValue)
                .orElse(null);
    }

    // ==================== canParse Tests ====================

    @Test
    @DisplayName("Should return true for non-empty text")
    void canParseShouldReturnTrueForNonEmpty() {
        assertTrue(parser.canParse("Some text"));
    }

    @Test
    @DisplayName("Should return false for null text")
    void canParseShouldReturnFalseForNull() {
        assertFalse(parser.canParse(null));
    }

    @Test
    @DisplayName("Should return false for empty text")
    void canParseShouldReturnFalseForEmpty() {
        assertFalse(parser.canParse(""));
        assertFalse(parser.canParse("   "));
    }
}
