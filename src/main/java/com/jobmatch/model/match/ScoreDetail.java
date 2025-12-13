package com.jobmatch.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Score detail for a single dimension.
 * Based on PRD v3.2 section 13.1.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreDetail {

    /**
     * Dimension name: skill, experience, bonus.
     */
    private String dimension;

    /**
     * Raw score for this dimension (0-100).
     */
    private int score;

    /**
     * Weight of this dimension.
     */
    private double weight;

    /**
     * Weighted score contribution.
     */
    @JsonProperty("weighted_score")
    private double weightedScore;

    /**
     * Individual items in this dimension.
     */
    @Builder.Default
    private List<ScoreItem> items = new ArrayList<>();

    /**
     * Summary of this dimension.
     */
    private String summary;

    /**
     * Calculate weighted score.
     */
    public void calculateWeightedScore() {
        this.weightedScore = this.score * this.weight;
    }

    /**
     * Score item within a dimension.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreItem {
        /**
         * Item name (e.g., skill name).
         */
        private String name;

        /**
         * Match status: matched, partial, missing.
         */
        private String status;

        /**
         * Points for this item.
         */
        private int points;

        /**
         * Max possible points.
         */
        @JsonProperty("max_points")
        private int maxPoints;

        /**
         * Reason for the score.
         */
        private String reason;

        /**
         * Evidence from resume.
         */
        private String evidence;
    }
}
