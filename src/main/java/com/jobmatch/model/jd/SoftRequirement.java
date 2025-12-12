package com.jobmatch.model.jd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft requirement from JD - preferred but not mandatory.
 * Based on PRD v3.2 section 13.1.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftRequirement {

    /**
     * Requirement type: skill, industry, trait, bonus.
     */
    private String type;

    /**
     * Requirement description.
     */
    private String requirement;

    /**
     * Weight indicator: 优先, 加分, 有...更好.
     */
    private String weight;

    /**
     * For skill type: skill name.
     */
    private String skill;

    /**
     * Evidence from original JD text.
     */
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    /**
     * Confidence score (0.0-1.0).
     */
    @Builder.Default
    private Double confidence = 0.0;

    // Weight constants
    public static final String WEIGHT_PREFERRED = "preferred";
    public static final String WEIGHT_BONUS = "bonus";
    public static final String WEIGHT_NICE_TO_HAVE = "nice_to_have";
}
