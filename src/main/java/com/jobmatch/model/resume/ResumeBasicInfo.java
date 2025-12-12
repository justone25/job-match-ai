package com.jobmatch.model.resume;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic information extracted from resume.
 * Based on PRD v3.2 section 13.1.1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeBasicInfo {

    /**
     * Total years of working experience.
     */
    @JsonProperty("experience_years")
    private Integer experienceYears;

    /**
     * Education level and major (e.g., "本科-计算机科学").
     */
    private String education;

    /**
     * Current job title.
     */
    @JsonProperty("current_title")
    private String currentTitle;

    /**
     * Industries the candidate has worked in.
     */
    @Builder.Default
    private List<String> industries = new ArrayList<>();

    /**
     * Evidence from original text.
     */
    @Builder.Default
    private List<String> evidence = new ArrayList<>();
}
