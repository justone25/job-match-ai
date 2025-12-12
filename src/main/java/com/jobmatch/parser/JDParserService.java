package com.jobmatch.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmatch.exception.JobMatchException;
import com.jobmatch.llm.LlmClient;
import com.jobmatch.llm.LlmException;
import com.jobmatch.llm.LlmRequest;
import com.jobmatch.llm.LlmResponse;
import com.jobmatch.model.common.ParseMeta;
import com.jobmatch.model.jd.*;
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
 * Service for parsing JD text into structured data.
 * Uses LLM for extraction with requirement classification.
 */
public class JDParserService {

    private static final Logger log = LoggerFactory.getLogger(JDParserService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PROMPT_TEMPLATE_PATH = "/prompts/jd_parse.txt";

    private final LlmClient llmClient;
    private final String promptTemplate;

    public JDParserService(LlmClient llmClient) {
        this.llmClient = llmClient;
        this.promptTemplate = loadPromptTemplate();
    }

    /**
     * Parse JD text into structured JDParsed object.
     *
     * @param jdText raw JD text content
     * @return parsed JD data
     * @throws JobMatchException if parsing fails
     */
    public JDParsed parse(String jdText) throws JobMatchException {
        if (jdText == null || jdText.trim().isEmpty()) {
            throw new JobMatchException(1002, "JD text is empty");
        }

        long startTime = System.currentTimeMillis();
        String contentHash = computeHash(jdText);

        log.info("Starting JD parsing, content hash: {}", contentHash.substring(0, 8));

        try {
            // Build prompt with JD content
            String prompt = promptTemplate.replace("{jd_text}", jdText);

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
            JDParsed result = parseJsonResponse(jsonContent);

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

            result.setOriginalText(jdText);
            result.setContentHash(contentHash);

            log.info("JD parsed successfully: {} hard, {} soft, {} implicit requirements, latency={}ms",
                    result.getHardRequirements().size(),
                    result.getSoftRequirements().size(),
                    result.getImplicitRequirements().size(),
                    latencyMs);

            return result;

        } catch (LlmException e) {
            log.error("LLM error during JD parsing: {}", e.getMessage());
            throw new JobMatchException(2002, "JD parsing failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during JD parsing", e);
            throw new JobMatchException(2002, "JD parsing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extract JSON from LLM response.
     */
    private String extractJson(String content) {
        if (content == null) {
            return "{}";
        }

        String trimmed = content.trim();

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
     * Parse JSON response into JDParsed object.
     */
    private JDParsed parseJsonResponse(String jsonContent) throws JobMatchException {
        try {
            JsonNode root = objectMapper.readTree(jsonContent);

            JDParsed result = JDParsed.builder()
                    .basicInfo(parseBasicInfo(root.get("basic_info")))
                    .hardRequirements(parseHardRequirements(root.get("hard_requirements")))
                    .softRequirements(parseSoftRequirements(root.get("soft_requirements")))
                    .implicitRequirements(parseImplicitRequirements(root.get("implicit_requirements")))
                    .build();

            if (root.has("ideal_candidate") && !root.get("ideal_candidate").isNull()) {
                result.setIdealCandidate(root.get("ideal_candidate").asText());
            }

            if (root.has("jd_quality_score")) {
                result.setJdQualityScore(root.get("jd_quality_score").asInt());
            }

            return result;

        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", e.getMessage());
            throw new JobMatchException(2005, "JSON output format error: " + e.getMessage(), e);
        }
    }

    private JDBasicInfo parseBasicInfo(JsonNode node) {
        if (node == null) {
            return JDBasicInfo.builder().build();
        }

        return JDBasicInfo.builder()
                .title(getTextOrNull(node, "title"))
                .company(getTextOrNull(node, "company"))
                .location(getTextOrNull(node, "location"))
                .salaryRange(getTextOrNull(node, "salary_range"))
                .build();
    }

    private List<HardRequirement> parseHardRequirements(JsonNode node) {
        List<HardRequirement> requirements = new ArrayList<>();
        if (node == null) {
            return requirements;
        }

        // Parse education requirement
        if (node.has("education") && !node.get("education").isNull()) {
            JsonNode edu = node.get("education");
            if (edu.has("requirement") && !edu.get("requirement").isNull()) {
                requirements.add(HardRequirement.builder()
                        .type(HardRequirement.TYPE_EDUCATION)
                        .requirement(edu.get("requirement").asText())
                        .evidence(parseStringArray(edu.get("evidence")))
                        .confidence(0.9)
                        .build());
            }
        }

        // Parse experience requirement
        if (node.has("years_of_experience") && !node.get("years_of_experience").isNull()) {
            JsonNode exp = node.get("years_of_experience");
            Integer min = exp.has("min") && !exp.get("min").isNull() ? exp.get("min").asInt() : null;
            Integer max = exp.has("max") && !exp.get("max").isNull() ? exp.get("max").asInt() : null;

            if (min != null) {
                String value = min + "年" + (max != null ? "-" + max + "年" : "+");
                requirements.add(HardRequirement.builder()
                        .type(HardRequirement.TYPE_EXPERIENCE)
                        .requirement(value + "工作经验")
                        .value(value)
                        .yearsRequired(min)
                        .evidence(parseStringArray(exp.get("evidence")))
                        .confidence(0.95)
                        .build());
            }
        }

        // Parse must-have skills
        if (node.has("must_have_skills") && node.get("must_have_skills").isArray()) {
            for (JsonNode skillNode : node.get("must_have_skills")) {
                requirements.add(HardRequirement.builder()
                        .type(HardRequirement.TYPE_SKILL)
                        .skill(getTextOrNull(skillNode, "name"))
                        .standardName(getTextOrNull(skillNode, "standard_name"))
                        .level(getTextOrNull(skillNode, "proficiency_required"))
                        .yearsRequired(skillNode.has("years_required") && !skillNode.get("years_required").isNull()
                                ? skillNode.get("years_required").asInt() : null)
                        .requirement(buildSkillRequirement(skillNode))
                        .evidence(parseStringArray(skillNode.get("evidence")))
                        .confidence(skillNode.has("confidence") ? skillNode.get("confidence").asDouble() : 0.8)
                        .build());
            }
        }

        // Parse industry requirements
        if (node.has("industry_requirements") && node.get("industry_requirements").isArray()) {
            for (JsonNode indNode : node.get("industry_requirements")) {
                requirements.add(HardRequirement.builder()
                        .type(HardRequirement.TYPE_INDUSTRY)
                        .requirement(getTextOrNull(indNode, "requirement"))
                        .evidence(parseStringArray(indNode.get("evidence")))
                        .confidence(0.85)
                        .build());
            }
        }

        return requirements;
    }

    private List<SoftRequirement> parseSoftRequirements(JsonNode node) {
        List<SoftRequirement> requirements = new ArrayList<>();
        if (node == null) {
            return requirements;
        }

        // Parse preferred skills
        if (node.has("preferred_skills") && node.get("preferred_skills").isArray()) {
            for (JsonNode skillNode : node.get("preferred_skills")) {
                requirements.add(SoftRequirement.builder()
                        .type("skill")
                        .skill(getTextOrNull(skillNode, "name"))
                        .weight(getTextOrNull(skillNode, "weight"))
                        .requirement(getTextOrNull(skillNode, "name") + " (优先)")
                        .evidence(parseStringArray(skillNode.get("evidence")))
                        .confidence(skillNode.has("confidence") ? skillNode.get("confidence").asDouble() : 0.7)
                        .build());
            }
        }

        // Parse bonus items
        if (node.has("bonus_items") && node.get("bonus_items").isArray()) {
            for (JsonNode bonusNode : node.get("bonus_items")) {
                requirements.add(SoftRequirement.builder()
                        .type(getTextOrNull(bonusNode, "type"))
                        .requirement(getTextOrNull(bonusNode, "description"))
                        .weight(SoftRequirement.WEIGHT_BONUS)
                        .evidence(parseStringArray(bonusNode.get("evidence")))
                        .confidence(0.75)
                        .build());
            }
        }

        return requirements;
    }

    private List<ImplicitRequirement> parseImplicitRequirements(JsonNode node) {
        List<ImplicitRequirement> requirements = new ArrayList<>();
        if (node == null) {
            return requirements;
        }

        // Parse inferred level
        if (node.has("inferred_level") && !node.get("inferred_level").isNull()) {
            requirements.add(ImplicitRequirement.builder()
                    .type(ImplicitRequirement.TYPE_LEVEL)
                    .inference(node.get("inferred_level").asText())
                    .reasoning("Based on job title and responsibilities")
                    .confidence(0.7)
                    .build());
        }

        // Parse management scope
        if (node.has("management_scope") && !node.get("management_scope").isNull()) {
            requirements.add(ImplicitRequirement.builder()
                    .type(ImplicitRequirement.TYPE_MANAGEMENT)
                    .inference(node.get("management_scope").asText())
                    .reasoning("Based on team-related keywords")
                    .confidence(0.65)
                    .build());
        }

        // Parse detailed inferences
        if (node.has("inferences") && node.get("inferences").isArray()) {
            for (JsonNode infNode : node.get("inferences")) {
                requirements.add(ImplicitRequirement.builder()
                        .type(getTextOrNull(infNode, "type"))
                        .inference(getTextOrNull(infNode, "inference"))
                        .reasoning(getTextOrNull(infNode, "reasoning"))
                        .evidence(parseStringArray(infNode.get("evidence")))
                        .confidence(infNode.has("confidence") ? infNode.get("confidence").asDouble() : 0.6)
                        .build());
            }
        }

        return requirements;
    }

    private String buildSkillRequirement(JsonNode skillNode) {
        StringBuilder sb = new StringBuilder();
        String name = getTextOrNull(skillNode, "name");
        String level = getTextOrNull(skillNode, "proficiency_required");

        if (level != null) {
            sb.append(level);
        }
        if (name != null) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(name);
        }

        return sb.toString();
    }

    private String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private List<String> parseStringArray(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private double calculateOverallConfidence(JDParsed result) {
        double sum = 0;
        int count = 0;

        for (HardRequirement req : result.getHardRequirements()) {
            if (req.getConfidence() != null) {
                sum += req.getConfidence();
                count++;
            }
        }

        for (SoftRequirement req : result.getSoftRequirements()) {
            if (req.getConfidence() != null) {
                sum += req.getConfidence();
                count++;
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

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
