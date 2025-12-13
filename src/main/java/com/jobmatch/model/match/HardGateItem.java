package com.jobmatch.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single hard gate requirement check result.
 * Based on PRD v3.2 section 13.1.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HardGateItem {

    /**
     * The requirement from JD.
     */
    private String requirement;

    /**
     * Type of requirement: experience, skill, education, certification, industry.
     */
    private String type;

    /**
     * Check result status.
     */
    private HardGateStatus status;

    /**
     * Candidate's value for this requirement.
     */
    @JsonProperty("candidate_value")
    private String candidateValue;

    /**
     * Required value from JD.
     */
    @JsonProperty("required_value")
    private String requiredValue;

    /**
     * Evidence from both JD and resume.
     */
    private Evidence evidence;

    /**
     * Explanation of the check result.
     */
    private String explanation;

    /**
     * Confidence of this check (0.0-1.0).
     */
    @Builder.Default
    private Double confidence = 0.0;

    /**
     * Evidence container.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Evidence {
        @JsonProperty("jd")
        private String jdEvidence;

        @JsonProperty("resume")
        private String resumeEvidence;
    }

    /**
     * Check if this item passed.
     */
    public boolean isPassed() {
        return status == HardGateStatus.PASS;
    }

    /**
     * Check if this item failed.
     */
    public boolean isFailed() {
        return status == HardGateStatus.FAIL;
    }

    /**
     * Check if this item is borderline.
     */
    public boolean isBorderline() {
        return status == HardGateStatus.BORDERLINE;
    }

    /**
     * Check if this item has unknown status.
     */
    public boolean isUnknown() {
        return status == HardGateStatus.UNKNOWN;
    }
}
