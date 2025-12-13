package com.jobmatch.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Gap analysis between resume and JD requirements.
 * Based on PRD v3.2 section 13.1.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GapAnalysis {

    /**
     * Missing skills/requirements (completely absent).
     */
    @Builder.Default
    private List<GapItem> missing = new ArrayList<>();

    /**
     * Insufficient skills (present but below required level).
     */
    @Builder.Default
    private List<GapItem> insufficient = new ArrayList<>();

    /**
     * Strengths (exceeds requirements or unique advantages).
     */
    @Builder.Default
    private List<StrengthItem> strengths = new ArrayList<>();

    /**
     * Gap item for missing or insufficient requirements.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GapItem {
        /**
         * Name of the gap (e.g., skill name).
         */
        private String name;

        /**
         * Type: skill, experience, certification, etc.
         */
        private String type;

        /**
         * Required level/value from JD.
         */
        @JsonProperty("required_level")
        private String requiredLevel;

        /**
         * Current level/value from resume (if applicable).
         */
        @JsonProperty("current_level")
        private String currentLevel;

        /**
         * Impact on matching (high, medium, low).
         */
        private String impact;

        /**
         * Evidence from JD.
         */
        @JsonProperty("jd_evidence")
        private String jdEvidence;

        /**
         * Suggestion to bridge the gap.
         */
        private String suggestion;

        /**
         * Priority for addressing (1=highest).
         */
        private int priority;
    }

    /**
     * Strength item for advantages.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrengthItem {
        /**
         * Name of the strength.
         */
        private String name;

        /**
         * Type: skill, experience, domain, etc.
         */
        private String type;

        /**
         * Description of the strength.
         */
        private String description;

        /**
         * How to highlight in interview/resume.
         */
        @JsonProperty("highlight_suggestion")
        private String highlightSuggestion;

        /**
         * Evidence from resume.
         */
        private String evidence;

        /**
         * Relevance to the position (high, medium, low).
         */
        private String relevance;
    }

    /**
     * Get total gap count.
     */
    public int getTotalGapCount() {
        return missing.size() + insufficient.size();
    }

    /**
     * Get high impact gap count.
     */
    public int getHighImpactGapCount() {
        int count = 0;
        for (GapItem item : missing) {
            if ("high".equalsIgnoreCase(item.getImpact())) count++;
        }
        for (GapItem item : insufficient) {
            if ("high".equalsIgnoreCase(item.getImpact())) count++;
        }
        return count;
    }

    /**
     * Check if there are any critical gaps.
     */
    public boolean hasCriticalGaps() {
        return getHighImpactGapCount() > 0;
    }
}
