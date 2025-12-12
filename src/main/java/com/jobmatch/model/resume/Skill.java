package com.jobmatch.model.resume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill extracted from resume.
 * Based on PRD v3.2 section 13.1.1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    /**
     * Original skill name as appeared in resume.
     */
    private String name;

    /**
     * Standardized skill name after normalization.
     */
    @JsonProperty("standard_name")
    private String standardName;

    /**
     * Proficiency level: expert, proficient, familiar, beginner, unknown.
     */
    @Builder.Default
    private String level = "unknown";

    /**
     * Years of experience with this skill.
     */
    private Integer years;

    /**
     * Evidence from original text supporting this skill.
     */
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    /**
     * Confidence score (0.0-1.0) for this extraction.
     */
    @Builder.Default
    private Double confidence = 0.0;

    /**
     * Proficiency level enum values.
     */
    public static final String LEVEL_EXPERT = "expert";
    public static final String LEVEL_PROFICIENT = "proficient";
    public static final String LEVEL_FAMILIAR = "familiar";
    public static final String LEVEL_BEGINNER = "beginner";
    public static final String LEVEL_UNKNOWN = "unknown";
}
