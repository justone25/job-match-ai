package com.jobmatch.model.jd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Hard requirement from JD - must be satisfied.
 * Based on PRD v3.2 section 13.1.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HardRequirement {

    /**
     * Requirement type: experience, skill, education, certification, industry.
     */
    private String type;

    /**
     * Requirement description.
     */
    private String requirement;

    /**
     * Specific value (e.g., "5年+", "本科及以上").
     */
    private String value;

    /**
     * For skill type: skill name.
     */
    private String skill;

    /**
     * For skill type: standardized skill name.
     */
    @JsonProperty("standard_name")
    private String standardName;

    /**
     * Required proficiency level.
     */
    private String level;

    /**
     * Required years of experience.
     */
    @JsonProperty("years_required")
    private Integer yearsRequired;

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

    // Type constants
    public static final String TYPE_EXPERIENCE = "experience";
    public static final String TYPE_SKILL = "skill";
    public static final String TYPE_EDUCATION = "education";
    public static final String TYPE_CERTIFICATION = "certification";
    public static final String TYPE_INDUSTRY = "industry";
}
