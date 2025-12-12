package com.jobmatch.model.resume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Project experience extracted from resume.
 * Based on PRD v3.2 section 13.1.1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    /**
     * Project name.
     */
    private String name;

    /**
     * Role in the project.
     */
    private String role;

    /**
     * Technology stack used.
     */
    @JsonProperty("tech_stack")
    @Builder.Default
    private List<String> techStack = new ArrayList<>();

    /**
     * Key achievements and outcomes.
     */
    @Builder.Default
    private List<String> achievements = new ArrayList<>();

    /**
     * Evidence from original text.
     */
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    /**
     * Confidence score (0.0-1.0).
     */
    @Builder.Default
    private Double confidence = 0.0;
}
