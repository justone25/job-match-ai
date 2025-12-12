package com.jobmatch.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of skill dictionary lookup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillLookupResult {

    /**
     * The original input skill name.
     */
    private String originalName;

    /**
     * The standardized skill name.
     */
    private String standardName;

    /**
     * Whether a match was found in dictionary.
     */
    private boolean found;

    /**
     * Source of the match: alias, translation, category, exact, or none.
     */
    private String matchSource;

    /**
     * Category of the skill if found.
     */
    private String category;

    /**
     * Confidence of the match (1.0 for dictionary, lower for fuzzy/LLM).
     */
    @Builder.Default
    private double confidence = 0.0;

    // Match source constants
    public static final String SOURCE_ALIAS = "alias";
    public static final String SOURCE_TRANSLATION = "translation";
    public static final String SOURCE_CATEGORY = "category";
    public static final String SOURCE_EXACT = "exact";
    public static final String SOURCE_LLM = "llm";
    public static final String SOURCE_NONE = "none";

    /**
     * Create a successful lookup result.
     */
    public static SkillLookupResult found(String original, String standard, String source) {
        return SkillLookupResult.builder()
                .originalName(original)
                .standardName(standard)
                .found(true)
                .matchSource(source)
                .confidence(1.0)
                .build();
    }

    /**
     * Create a not-found result.
     */
    public static SkillLookupResult notFound(String original) {
        return SkillLookupResult.builder()
                .originalName(original)
                .standardName(original)
                .found(false)
                .matchSource(SOURCE_NONE)
                .confidence(0.0)
                .build();
    }
}
