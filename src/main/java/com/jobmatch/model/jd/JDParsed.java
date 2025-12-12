package com.jobmatch.model.jd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobmatch.model.common.ParseMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete parsed JD result.
 * Based on PRD v3.2 section 13.1.2 JDParsed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JDParsed {

    /**
     * Basic information: title, company, location, salary.
     */
    @JsonProperty("basic_info")
    private JDBasicInfo basicInfo;

    /**
     * Hard requirements - must be satisfied.
     */
    @JsonProperty("hard_requirements")
    @Builder.Default
    private List<HardRequirement> hardRequirements = new ArrayList<>();

    /**
     * Soft requirements - preferred but not mandatory.
     */
    @JsonProperty("soft_requirements")
    @Builder.Default
    private List<SoftRequirement> softRequirements = new ArrayList<>();

    /**
     * Implicit requirements - inferred from context.
     */
    @JsonProperty("implicit_requirements")
    @Builder.Default
    private List<ImplicitRequirement> implicitRequirements = new ArrayList<>();

    /**
     * Ideal candidate profile description.
     */
    @JsonProperty("ideal_candidate")
    private String idealCandidate;

    /**
     * Parse metadata.
     */
    @JsonProperty("parse_meta")
    private ParseMeta parseMeta;

    /**
     * JD quality score (1-10).
     */
    @JsonProperty("jd_quality_score")
    private Integer jdQualityScore;

    /**
     * Original JD text.
     */
    @JsonProperty("original_text")
    private String originalText;

    /**
     * Content hash for caching.
     */
    @JsonProperty("content_hash")
    private String contentHash;

    /**
     * Get count of all requirements.
     */
    public int getTotalRequirementsCount() {
        return (hardRequirements != null ? hardRequirements.size() : 0) +
                (softRequirements != null ? softRequirements.size() : 0) +
                (implicitRequirements != null ? implicitRequirements.size() : 0);
    }
}
