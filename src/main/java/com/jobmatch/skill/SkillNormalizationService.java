package com.jobmatch.skill;

import com.jobmatch.llm.LlmClient;
import com.jobmatch.llm.LlmException;
import com.jobmatch.llm.LlmRequest;
import com.jobmatch.llm.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for normalizing skill names.
 * Based on PRD v3.2 section 13.3.2.
 *
 * Normalization flow:
 * 1. Preprocess input (trim, normalize whitespace)
 * 2. Dictionary lookup (exact and case-insensitive)
 * 3. LLM fallback if dictionary miss
 * 4. Cache result
 */
public class SkillNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(SkillNormalizationService.class);
    private static final String PROMPT_TEMPLATE_PATH = "/prompts/skill_normalize.txt";
    private static final String UNKNOWN_MARKER = "UNKNOWN";
    private static final double LLM_CONFIDENCE = 0.8;

    private final SkillDictionaryService dictionaryService;
    private final LlmClient llmClient;
    private final String promptTemplate;

    // Cache for LLM normalization results
    private final Map<String, SkillLookupResult> llmCache;

    /**
     * Create service with dictionary and LLM client.
     */
    public SkillNormalizationService(SkillDictionaryService dictionaryService, LlmClient llmClient) {
        this.dictionaryService = dictionaryService;
        this.llmClient = llmClient;
        this.promptTemplate = loadPromptTemplate();
        this.llmCache = new ConcurrentHashMap<>();
    }

    /**
     * Create service with default dictionary and specified LLM client.
     */
    public SkillNormalizationService(LlmClient llmClient) {
        this(SkillDictionaryService.getInstance(), llmClient);
    }

    /**
     * Normalize a skill name using dictionary + LLM fallback.
     *
     * @param skillName raw skill name
     * @return normalized result
     */
    public SkillLookupResult normalize(String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return SkillLookupResult.notFound(skillName);
        }

        String trimmed = skillName.trim();

        // Step 1: Try dictionary lookup
        SkillLookupResult dictResult = dictionaryService.lookup(trimmed);
        if (dictResult.isFound()) {
            log.debug("Skill '{}' normalized via dictionary to '{}'",
                    skillName, dictResult.getStandardName());
            return dictResult;
        }

        // Step 2: Check LLM cache
        SkillLookupResult cachedLlm = llmCache.get(trimmed.toLowerCase());
        if (cachedLlm != null) {
            log.debug("Skill '{}' found in LLM cache", skillName);
            return cachedLlm;
        }

        // Step 3: LLM fallback
        SkillLookupResult llmResult = normalizeLLM(trimmed, skillName);

        // Cache LLM result
        llmCache.put(trimmed.toLowerCase(), llmResult);

        return llmResult;
    }

    /**
     * Normalize without LLM fallback (dictionary only).
     */
    public SkillLookupResult normalizeDictionaryOnly(String skillName) {
        return dictionaryService.lookup(skillName);
    }

    /**
     * Batch normalize skills.
     */
    public List<SkillLookupResult> normalizeAll(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return List.of();
        }

        return skillNames.stream()
                .map(this::normalize)
                .toList();
    }

    /**
     * Get the standardized name only.
     */
    public String standardize(String skillName) {
        return normalize(skillName).getStandardName();
    }

    /**
     * Clear all caches.
     */
    public void clearCache() {
        dictionaryService.clearCache();
        llmCache.clear();
        log.debug("All normalization caches cleared");
    }

    /**
     * Get LLM cache size.
     */
    public int getLlmCacheSize() {
        return llmCache.size();
    }

    // Private methods

    private SkillLookupResult normalizeLLM(String trimmed, String original) {
        if (llmClient == null) {
            log.debug("No LLM client configured, returning original for '{}'", original);
            return SkillLookupResult.notFound(original);
        }

        try {
            String prompt = promptTemplate.replace("{skill_name}", trimmed);

            LlmRequest request = LlmRequest.builder()
                    .messages(List.of(LlmRequest.Message.user(prompt)))
                    .temperature(0.0)  // Deterministic output
                    .maxTokens(50)     // Short response expected
                    .build();

            LlmResponse response = llmClient.chat(request);
            String standardized = extractStandardName(response.getContent());

            // Check if LLM returned unknown
            if (standardized == null || standardized.equalsIgnoreCase(UNKNOWN_MARKER)) {
                log.debug("LLM could not normalize skill '{}'", original);
                return SkillLookupResult.notFound(original);
            }

            // Verify LLM result against dictionary for category
            SkillLookupResult verification = dictionaryService.lookup(standardized);
            String category = verification.isFound() ? verification.getCategory() : null;

            log.info("Skill '{}' normalized via LLM to '{}' (category: {})",
                    original, standardized, category);

            SkillLookupResult result = SkillLookupResult.builder()
                    .originalName(original)
                    .standardName(standardized)
                    .found(true)
                    .matchSource(SkillLookupResult.SOURCE_LLM)
                    .category(category)
                    .confidence(LLM_CONFIDENCE)
                    .build();

            return result;

        } catch (LlmException e) {
            log.warn("LLM normalization failed for '{}': {}", original, e.getMessage());
            return SkillLookupResult.notFound(original);
        }
    }

    private String extractStandardName(String response) {
        if (response == null) {
            return null;
        }

        // Take only the first line and trim
        String result = response.trim();
        int newlineIndex = result.indexOf('\n');
        if (newlineIndex > 0) {
            result = result.substring(0, newlineIndex);
        }

        // Remove any surrounding quotes
        result = result.replace("\"", "").replace("'", "").trim();

        return result.isEmpty() ? null : result;
    }

    private String loadPromptTemplate() {
        try (InputStream is = getClass().getResourceAsStream(PROMPT_TEMPLATE_PATH)) {
            if (is == null) {
                log.warn("Prompt template not found at {}", PROMPT_TEMPLATE_PATH);
                return "Normalize this skill name to its standard form. Return only the name: {skill_name}";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", e.getMessage());
            return "Normalize this skill name to its standard form. Return only the name: {skill_name}";
        }
    }
}
