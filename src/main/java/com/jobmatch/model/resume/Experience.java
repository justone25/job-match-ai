package com.jobmatch.model.resume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Work experience extracted from resume.
 * Based on PRD v3.2 section 13.1.1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experience {

    /**
     * Company name.
     */
    private String company;

    /**
     * Job title at this company.
     */
    private String title;

    /**
     * Duration of employment (e.g., "2020.03-至今").
     */
    private String duration;

    /**
     * Industry of the company.
     */
    private String industry;

    /**
     * Business domains worked on.
     */
    @Builder.Default
    private List<String> domain = new ArrayList<>();

    /**
     * Key highlights and achievements.
     */
    @Builder.Default
    private List<String> highlights = new ArrayList<>();

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
