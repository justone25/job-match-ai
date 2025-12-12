package com.jobmatch.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmatch.exception.JobMatchException;
import com.jobmatch.llm.LlmClient;
import com.jobmatch.llm.LlmException;
import com.jobmatch.llm.LlmRequest;
import com.jobmatch.llm.LlmResponse;
import com.jobmatch.model.common.ParseMeta;
import com.jobmatch.model.resume.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing resume text into structured data.
 * Uses LLM for extraction with JSON output.
 */
public class ResumeParserService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParserService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PROMPT_TEMPLATE_PATH = "/prompts/resume_parse.txt";

    private final LlmClient llmClient;
    private final String promptTemplate;

    public ResumeParserService(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.promptTemplate = loadPromptTemplate();
    }

    /**
     * Parse resume text into structured ResumeParsed object.
     *
     * @param resumeText raw resume text content
     * @return parsed resume data
     * @throws JobMatchException if parsing fails
     */
    public ResumeParsed parse(String resumeText) throws JobMatchException {
        if (resumeText == null || resumeText.trim().isEmpty()) {
            throw new JobMatchException(1001, "Resume text is empty");
        }

        long startTime = System.currentTimeMillis();
        String contentHash = computeHash(resumeText);

        log.info("Starting resume parsing, content hash: {}", contentHash.substring(0, 8));

        try {
            // Build prompt with resume content
            String prompt = promptTemplate.replace("{resume_text}", resumeText);

            // Call LLM
            LlmRequest request = LlmRequest.builder()
                    .messages(List.of(LlmRequest.Message.user(prompt)))
                    .temperature(0.1)
                    .maxTokens(4096)
                    .jsonMode(true)
                    .build();

            LlmResponse response = llmClient.chat(request);
            String jsonContent = extractJson(response.getContent());

            // Parse JSON response
            ResumeParsed result = parseJsonResponse(jsonContent, resumeText);

            // Set metadata
            long latencyMs = System.currentTimeMillis() - startTime;
            result.setParseMeta(ParseMeta.builder()
                    .parseTime(Instant.now())
                    .modelVersion("v0.1")
                    .llmProvider(llmClient.getProviderName())
                    .totalTokens(response.getTotalTokens())
                    .latencyMs(latencyMs)
                    .overallConfidence(calculateOverallConfidence(result))
                    .fromCache(false)
                    .build());

            result.setOriginalText(resumeText);
            result.setContentHash(contentHash);

            log.info("Resume parsed successfully: {} skills, {} experiences, latency={}ms",
                    result.getSkills().size(),
                    result.getExperiences().size(),
                    latencyMs);

            return result;

        } catch (LlmException e) {
            log.error("LLM error during resume parsing: {}", e.getMessage());
            throw new JobMatchException(2001, "Resume parsing failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during resume parsing", e);
            throw new JobMatchException(2001, "Resume parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract JSON from LLM response (handles markdown code blocks).
     */
    private String extractJson(String content) {
        if (content == null) {
            return "{}";
        }

        String trimmed = content.trim();

        // Remove markdown code block if present
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        return trimmed.trim();
    }

    /**
     * Parse JSON response into ResumeParsed object.
     */
    private ResumeParsed parseJsonResponse(String jsonContent, String originalText) throws JobMatchException {
        try {
            JsonNode root = objectMapper.readTree(jsonContent);

            ResumeParsed result = ResumeParsed.builder()
                    .basicInfo(parseBasicInfo(root.get("basic_info")))
                    .skills(parseSkills(root.get("skills")))
                    .experiences(parseExperiences(root.get("experience")))
                    .projects(parseProjects(root.get("projects")))
                    .build();

            return result;

        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", e.getMessage());
            throw new JobMatchException(2005, "JSON output format error: " + e.getMessage(), e);
        }
    }

    private ResumeBasicInfo parseBasicInfo(JsonNode node) {
        if (node == null) {
            return ResumeBasicInfo.builder().build();
        }

        ResumeBasicInfo.ResumeBasicInfoBuilder builder = ResumeBasicInfo.builder();

        if (node.has("years_of_experience") && !node.get("years_of_experience").isNull()) {
            builder.experienceYears(node.get("years_of_experience").asInt());
        }

        if (node.has("education") && node.get("education").isObject()) {
            JsonNode edu = node.get("education");
            StringBuilder eduStr = new StringBuilder();
            if (edu.has("degree") && !edu.get("degree").isNull()) {
                eduStr.append(edu.get("degree").asText());
            }
            if (edu.has("major") && !edu.get("major").isNull()) {
                if (eduStr.length() > 0) eduStr.append("-");
                eduStr.append(edu.get("major").asText());
            }
            if (eduStr.length() > 0) {
                builder.education(eduStr.toString());
            }
        }

        if (node.has("current_title") && !node.get("current_title").isNull()) {
            builder.currentTitle(node.get("current_title").asText());
        }

        if (node.has("industries") && node.get("industries").isArray()) {
            List<String> industries = new ArrayList<>();
            node.get("industries").forEach(n -> industries.add(n.asText()));
            builder.industries(industries);
        }

        if (node.has("evidence") && node.get("evidence").isArray()) {
            List<String> evidence = new ArrayList<>();
            node.get("evidence").forEach(n -> evidence.add(n.asText()));
            builder.evidence(evidence);
        }

        return builder.build();
    }

    private List<Skill> parseSkills(JsonNode node) {
        List<Skill> skills = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return skills;
        }

        for (JsonNode skillNode : node) {
            Skill.SkillBuilder builder = Skill.builder();

            if (skillNode.has("name")) {
                builder.name(skillNode.get("name").asText());
            }
            if (skillNode.has("standard_name")) {
                builder.standardName(skillNode.get("standard_name").asText());
            }
            if (skillNode.has("proficiency")) {
                builder.level(skillNode.get("proficiency").asText());
            }
            if (skillNode.has("years") && !skillNode.get("years").isNull()) {
                builder.years(skillNode.get("years").asInt());
            }
            if (skillNode.has("confidence")) {
                builder.confidence(skillNode.get("confidence").asDouble());
            }
            if (skillNode.has("evidence") && skillNode.get("evidence").isArray()) {
                List<String> evidence = new ArrayList<>();
                skillNode.get("evidence").forEach(n -> evidence.add(n.asText()));
                builder.evidence(evidence);
            }

            skills.add(builder.build());
        }

        return skills;
    }

    private List<Experience> parseExperiences(JsonNode node) {
        List<Experience> experiences = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return experiences;
        }

        for (JsonNode expNode : node) {
            Experience.ExperienceBuilder builder = Experience.builder();

            if (expNode.has("company")) {
                builder.company(expNode.get("company").asText());
            }
            if (expNode.has("title")) {
                builder.title(expNode.get("title").asText());
            }
            if (expNode.has("duration")) {
                builder.duration(expNode.get("duration").asText());
            }
            if (expNode.has("industry") && !expNode.get("industry").isNull()) {
                builder.industry(expNode.get("industry").asText());
            }
            if (expNode.has("domain") && expNode.get("domain").isArray()) {
                List<String> domain = new ArrayList<>();
                expNode.get("domain").forEach(n -> domain.add(n.asText()));
                builder.domain(domain);
            }
            if (expNode.has("highlights") && expNode.get("highlights").isArray()) {
                List<String> highlights = new ArrayList<>();
                expNode.get("highlights").forEach(n -> highlights.add(n.asText()));
                builder.highlights(highlights);
            }
            if (expNode.has("evidence") && expNode.get("evidence").isArray()) {
                List<String> evidence = new ArrayList<>();
                expNode.get("evidence").forEach(n -> evidence.add(n.asText()));
                builder.evidence(evidence);
            }
            if (expNode.has("confidence")) {
                builder.confidence(expNode.get("confidence").asDouble());
            }

            experiences.add(builder.build());
        }

        return experiences;
    }

    private List<Project> parseProjects(JsonNode node) {
        List<Project> projects = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return projects;
        }

        for (JsonNode projNode : node) {
            Project.ProjectBuilder builder = Project.builder();

            if (projNode.has("name")) {
                builder.name(projNode.get("name").asText());
            }
            if (projNode.has("role") && !projNode.get("role").isNull()) {
                builder.role(projNode.get("role").asText());
            }
            if (projNode.has("tech_stack") && projNode.get("tech_stack").isArray()) {
                List<String> techStack = new ArrayList<>();
                projNode.get("tech_stack").forEach(n -> techStack.add(n.asText()));
                builder.techStack(techStack);
            }
            if (projNode.has("achievements") && projNode.get("achievements").isArray()) {
                List<String> achievements = new ArrayList<>();
                projNode.get("achievements").forEach(n -> achievements.add(n.asText()));
                builder.achievements(achievements);
            }
            if (projNode.has("evidence") && projNode.get("evidence").isArray()) {
                List<String> evidence = new ArrayList<>();
                projNode.get("evidence").forEach(n -> evidence.add(n.asText()));
                builder.evidence(evidence);
            }
            if (projNode.has("confidence")) {
                builder.confidence(projNode.get("confidence").asDouble());
            }

            projects.add(builder.build());
        }

        return projects;
    }

    /**
     * Calculate overall confidence from parsed result.
     */
    private double calculateOverallConfidence(ResumeParsed result) {
        double sum = 0;
        int count = 0;

        for (Skill skill : result.getSkills()) {
            if (skill.getConfidence() != null) {
                sum += skill.getConfidence();
                count++;
            }
        }

        for (Experience exp : result.getExperiences()) {
            if (exp.getConfidence() != null) {
                sum += exp.getConfidence();
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    /**
     * Compute SHA-256 hash of content.
     */
    private String computeHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    /**
     * Load prompt template from resources.
     */
    private String loadPromptTemplate() {
        try (InputStream is = getClass().getResourceAsStream(PROMPT_TEMPLATE_PATH)) {
            if (is == null) {
                throw new RuntimeException("Prompt template not found: " + PROMPT_TEMPLATE_PATH);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt template", e);
        }
    }
}
