package com.jobmatch.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobmatch.config.AppConfig;
import com.jobmatch.config.ConfigLoader;
import com.jobmatch.config.SkillImplicationConfig;
import com.jobmatch.config.SkillImplicationLoader;
import com.jobmatch.llm.*;
import com.jobmatch.storage.StorageService;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Service for skill implication/association reasoning.
 * Uses configurable rules from skill_implications.yaml with LLM fallback.
 *
 * Match types (priority):
 * 1. DIRECT - candidate skill directly matches required skill
 * 2. RULE - rule-based implication from configuration
 * 3. LLM - LLM-inferred relationship (cached)
 * 4. NONE - no match found
 */
public class SkillImplicationService {

    private static final Logger log = LoggerFactory.getLogger(SkillImplicationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SkillImplicationLoader configLoader;
    private final LlmClient llmClient;
    private final StorageService storageService;
    private final boolean llmFallbackEnabled;

    /**
     * Create service with default configuration.
     */
    public SkillImplicationService() {
        this(SkillImplicationLoader.getInstance(), null, StorageService.getInstance());
    }

    /**
     * Create service with LLM client for fallback.
     */
    public SkillImplicationService(LlmClient llmClient) {
        this(SkillImplicationLoader.getInstance(), llmClient, StorageService.getInstance());
    }

    /**
     * Create service with custom dependencies (for testing).
     */
    public SkillImplicationService(SkillImplicationLoader configLoader,
                                   LlmClient llmClient,
                                   StorageService storageService) {
        this.configLoader = configLoader;
        this.llmClient = llmClient;
        this.storageService = storageService;
        this.llmFallbackEnabled = configLoader.isLlmFallbackEnabled() && llmClient != null;

        log.debug("SkillImplicationService initialized: rules={}, llm_fallback={}",
                configLoader.getConfig().getRuleCount(), llmFallbackEnabled);
    }

    /**
     * Get all skills that imply a given category/broader skill.
     */
    public List<String> getSkillsImplyingCategory(String category) {
        return configLoader.getSkillsImplyingCategory(category);
    }

    /**
     * Get all categories/broader skills implied by a specific skill.
     */
    public List<String> getImpliedCategories(String skill) {
        if (skill == null) return Collections.emptyList();

        List<SkillImplicationConfig.ImplicationRule> rules =
                configLoader.getRulesForTrigger(skill);

        if (rules.isEmpty()) {
            return Collections.emptyList();
        }

        // Collect all implied skills from matching rules
        Set<String> implied = new LinkedHashSet<>();
        for (SkillImplicationConfig.ImplicationRule rule : rules) {
            if (rule.getImplies() != null) {
                implied.addAll(rule.getImplies());
            }
        }
        return new ArrayList<>(implied);
    }

    /**
     * Check if candidate's skills imply knowledge of a required skill.
     *
     * @param candidateSkills List of skills the candidate has
     * @param requiredSkill The skill required by the job
     * @return ImplicationResult with match status, type, confidence and evidence
     */
    public ImplicationResult checkImplication(List<String> candidateSkills, String requiredSkill) {
        if (candidateSkills == null || candidateSkills.isEmpty() || requiredSkill == null) {
            return ImplicationResult.noMatch();
        }

        String reqLower = requiredSkill.toLowerCase();

        // 1. Direct match check
        for (String skill : candidateSkills) {
            if (skill.toLowerCase().contains(reqLower) || reqLower.contains(skill.toLowerCase())) {
                return ImplicationResult.directMatch(skill,
                        configLoader.getConfig().getSettings().getDefaultConfidence());
            }
        }

        // 2. Rule-based implication check
        ImplicationResult ruleResult = checkRuleBasedImplication(candidateSkills, requiredSkill);
        if (ruleResult.isMatched()) {
            return ruleResult;
        }

        // 3. Keyword matching (as fallback before LLM)
        ImplicationResult keywordResult = checkKeywordMatch(candidateSkills, requiredSkill);
        if (keywordResult.isMatched()) {
            return keywordResult;
        }

        // 4. LLM fallback (if enabled)
        if (llmFallbackEnabled) {
            return checkLlmFallback(candidateSkills, requiredSkill);
        }

        return ImplicationResult.noMatch();
    }

    /**
     * Check rule-based implication.
     */
    private ImplicationResult checkRuleBasedImplication(List<String> candidateSkills, String requiredSkill) {
        String reqLower = requiredSkill.toLowerCase();
        List<String> matchingSkills = new ArrayList<>();
        double maxConfidence = 0.0;
        String bestEvidence = null;

        for (String skill : candidateSkills) {
            List<SkillImplicationConfig.ImplicationRule> rules =
                    configLoader.getRulesForTrigger(skill);

            for (SkillImplicationConfig.ImplicationRule rule : rules) {
                if (rule.getImplies() == null) continue;

                for (String implied : rule.getImplies()) {
                    if (implied.toLowerCase().contains(reqLower) ||
                            reqLower.contains(implied.toLowerCase())) {
                        if (!matchingSkills.contains(skill)) {
                            matchingSkills.add(skill);
                        }
                        if (rule.getConfidence() > maxConfidence) {
                            maxConfidence = rule.getConfidence();
                            bestEvidence = rule.generateEvidence(skill, implied);
                        }
                    }
                }
            }
        }

        // Also check reverse: if required skill is a trigger for rules
        List<String> skillsImplyingReq = getSkillsImplyingCategory(requiredSkill);
        for (String skill : candidateSkills) {
            String skillLower = skill.toLowerCase();
            for (String implyingSkill : skillsImplyingReq) {
                if (skillLower.contains(implyingSkill) || implyingSkill.contains(skillLower)) {
                    if (!matchingSkills.contains(skill)) {
                        matchingSkills.add(skill);
                    }
                }
            }
        }

        if (!matchingSkills.isEmpty()) {
            return ImplicationResult.ruleMatch(matchingSkills,
                    maxConfidence > 0 ? maxConfidence : configLoader.getConfig().getSettings().getDefaultConfidence(),
                    bestEvidence != null ? bestEvidence :
                            String.join(", ", matchingSkills) + " 可推断具备 " + requiredSkill);
        }

        return ImplicationResult.noMatch();
    }

    /**
     * Check keyword-based matching.
     */
    private ImplicationResult checkKeywordMatch(List<String> candidateSkills, String requiredSkill) {
        String[] reqKeywords = extractKeywords(requiredSkill);
        List<String> matchingSkills = new ArrayList<>();

        for (String skill : candidateSkills) {
            String skillLower = skill.toLowerCase();
            for (String keyword : reqKeywords) {
                if (skillLower.contains(keyword.toLowerCase())) {
                    if (!matchingSkills.contains(skill)) {
                        matchingSkills.add(skill);
                    }
                }
            }
        }

        if (!matchingSkills.isEmpty()) {
            double keywordConfidence = 0.70; // Lower confidence for keyword match
            return ImplicationResult.keywordMatch(matchingSkills, keywordConfidence,
                    String.join(", ", matchingSkills) + " 包含关键词匹配");
        }

        return ImplicationResult.noMatch();
    }

    /**
     * Check LLM fallback for skill implication.
     */
    private ImplicationResult checkLlmFallback(List<String> candidateSkills, String requiredSkill) {
        if (llmClient == null || !llmClient.isAvailable()) {
            log.debug("LLM fallback skipped: client not available");
            return ImplicationResult.noMatch();
        }

        SkillImplicationConfig.LlmFallbackConfig llmConfig = configLoader.getLlmFallbackConfig();
        String rulesVersion = configLoader.getVersion();

        // Try each candidate skill
        for (String candidateSkill : candidateSkills) {
            // Check cache first
            String cacheKey = generateCacheKey(candidateSkill, requiredSkill, rulesVersion);
            Optional<LlmImplicationResult> cached = storageService.getFromCache(cacheKey, LlmImplicationResult.class);

            if (cached.isPresent()) {
                LlmImplicationResult result = cached.get();
                if (result.canImply && result.confidence >= llmConfig.getMinConfidence()) {
                    log.debug("LLM cache hit: {} -> {} (confidence={})",
                            candidateSkill, requiredSkill, result.confidence);
                    return ImplicationResult.llmMatch(candidateSkill, result.confidence, result.reasoning);
                }
                continue; // Cached as not matching
            }

            // Call LLM
            try {
                LlmImplicationResult llmResult = callLlmForImplication(candidateSkill, requiredSkill, llmConfig);

                // Cache result
                storageService.saveToCache(cacheKey, llmResult);

                if (llmResult.canImply && llmResult.confidence >= llmConfig.getMinConfidence()) {
                    log.info("LLM inferred: {} -> {} (confidence={})",
                            candidateSkill, requiredSkill, llmResult.confidence);
                    return ImplicationResult.llmMatch(candidateSkill, llmResult.confidence, llmResult.reasoning);
                }
            } catch (Exception e) {
                log.warn("LLM fallback failed for {} -> {}: {}",
                        candidateSkill, requiredSkill, e.getMessage());
            }
        }

        return ImplicationResult.noMatch();
    }

    /**
     * Call LLM to check skill implication.
     */
    private LlmImplicationResult callLlmForImplication(String candidateSkill,
                                                        String requiredSkill,
                                                        SkillImplicationConfig.LlmFallbackConfig config)
            throws LlmException {

        String prompt = config.getPromptTemplate()
                .replace("{candidate_skill}", candidateSkill)
                .replace("{required_skill}", requiredSkill);

        LlmRequest request = LlmRequest.builder()
                .messages(List.of(LlmRequest.Message.user(prompt)))
                .temperature(0.0) // Deterministic output
                .jsonMode(true)
                .maxTokens(256)
                .build();

        LlmResponse response = llmClient.chat(request);
        String content = response.getContent().trim();

        try {
            return objectMapper.readValue(content, LlmImplicationResult.class);
        } catch (Exception e) {
            log.warn("Failed to parse LLM response: {}", content);
            return new LlmImplicationResult(false, 0.0, "Parse error");
        }
    }

    /**
     * Generate cache key for LLM result.
     */
    private String generateCacheKey(String candidateSkill, String requiredSkill, String rulesVersion) {
        String input = candidateSkill.toLowerCase() + "|" + requiredSkill.toLowerCase() + "|" + rulesVersion;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("llm_impl_");
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "llm_impl_" + input.hashCode();
        }
    }

    /**
     * Extract keywords from a skill requirement.
     */
    private String[] extractKeywords(String skill) {
        return Arrays.stream(skill.split("[\\s,;/()（）]+"))
                .filter(s -> s.length() >= 2)
                .toArray(String[]::new);
    }

    /**
     * LLM implication result structure.
     */
    @Data
    public static class LlmImplicationResult {
        @JsonProperty("can_imply")
        private boolean canImply;

        @JsonProperty("confidence")
        private double confidence;

        @JsonProperty("reasoning")
        private String reasoning;

        public LlmImplicationResult() {}

        public LlmImplicationResult(boolean canImply, double confidence, String reasoning) {
            this.canImply = canImply;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
    }

    /**
     * Match type enumeration.
     */
    public enum MatchType {
        DIRECT,   // Direct skill match
        RULE,     // Rule-based implication
        KEYWORD,  // Keyword matching
        LLM,      // LLM-inferred
        NONE      // No match
    }

    /**
     * Result of skill implication check.
     */
    public static class ImplicationResult {
        private final boolean matched;
        private final MatchType matchType;
        private final List<String> matchingSkills;
        private final double confidence;
        private final String evidence;

        private ImplicationResult(boolean matched, MatchType matchType,
                                  List<String> matchingSkills, double confidence, String evidence) {
            this.matched = matched;
            this.matchType = matchType;
            this.matchingSkills = matchingSkills;
            this.confidence = confidence;
            this.evidence = evidence;
        }

        public static ImplicationResult directMatch(String skill, double confidence) {
            return new ImplicationResult(true, MatchType.DIRECT, List.of(skill),
                    Math.max(confidence, 0.95), skill);
        }

        public static ImplicationResult ruleMatch(List<String> skills, double confidence, String evidence) {
            return new ImplicationResult(true, MatchType.RULE, skills, confidence, evidence);
        }

        public static ImplicationResult keywordMatch(List<String> skills, double confidence, String evidence) {
            return new ImplicationResult(true, MatchType.KEYWORD, skills, confidence, evidence);
        }

        public static ImplicationResult llmMatch(String skill, double confidence, String evidence) {
            return new ImplicationResult(true, MatchType.LLM, List.of(skill), confidence, evidence);
        }

        public static ImplicationResult noMatch() {
            return new ImplicationResult(false, MatchType.NONE, Collections.emptyList(), 0.0, null);
        }

        // Legacy compatibility methods

        /**
         * @deprecated Use {@link #getMatchType()} == DIRECT instead
         */
        @Deprecated
        public boolean isDirect() {
            return matchType == MatchType.DIRECT;
        }

        public boolean isMatched() {
            return matched;
        }

        public MatchType getMatchType() {
            return matchType;
        }

        public List<String> getMatchingSkills() {
            return matchingSkills;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getEvidence() {
            if (evidence != null) {
                return evidence;
            }
            if (matchingSkills.isEmpty()) {
                return null;
            }
            if (matchType == MatchType.DIRECT) {
                return matchingSkills.get(0);
            }
            String suffix = switch (matchType) {
                case RULE -> "(规则推断)";
                case KEYWORD -> "(关键词匹配)";
                case LLM -> "(AI推断)";
                default -> "(可推断)";
            };
            return String.join(", ", matchingSkills) + " " + suffix;
        }
    }
}
