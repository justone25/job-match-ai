package com.jobmatch.model.resume;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobmatch.model.common.ParseMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete parsed resume result.
 * Based on PRD v3.2 section 13.1.1 ResumeParsed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeParsed {

    /**
     * Basic information: years of experience, education, current title.
     */
    @JsonProperty("basic_info")
    private ResumeBasicInfo basicInfo;

    /**
     * Skills list with standardized names, proficiency levels, and evidence.
     */
    @Builder.Default
    private List<Skill> skills = new ArrayList<>();

    /**
     * Work experience history.
     */
    @JsonProperty("experience")
    @Builder.Default
    private List<Experience> experiences = new ArrayList<>();

    /**
     * Project experience.
     */
    @Builder.Default
    private List<Project> projects = new ArrayList<>();

    /**
     * Parse metadata: time, model version, confidence, etc.
     */
    @JsonProperty("parse_meta")
    private ParseMeta parseMeta;

    /**
     * Original resume text (for evidence validation).
     */
    @JsonProperty("original_text")
    private String originalText;

    /**
     * Content hash for caching.
     */
    @JsonProperty("content_hash")
    private String contentHash;
}
