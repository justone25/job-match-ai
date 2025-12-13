package com.jobmatch.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jobmatch.model.common.ParseMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Complete match analysis report.
 * Based on PRD v3.2 section 13.1.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchReport {

    /**
     * Summary of the match analysis.
     */
    private Summary summary;

    /**
     * Hard gate check result.
     */
    @JsonProperty("hard_gate")
    private HardGateResult hardGate;

    /**
     * Soft score calculation result.
     */
    private SoftScoreResult scores;

    /**
     * Gap analysis.
     */
    private GapAnalysis gaps;

    /**
     * Action suggestions.
     */
    private ActionSuggestion actions;

    /**
     * Report metadata.
     */
    private ParseMeta meta;

    /**
     * Report summary.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        /**
         * Recommendation: 建议投递, 不建议投递, 信息不足.
         */
        private String recommendation;

        /**
         * Overall score (0-100).
         */
        @JsonProperty("overall_score")
        private int overallScore;

        /**
         * Hard gate status: passed, failed, uncertain.
         */
        @JsonProperty("hard_gate_status")
        private String hardGateStatus;

        /**
         * Match level: A, B, C, D.
         */
        @JsonProperty("match_level")
        private String matchLevel;

        /**
         * One-line summary.
         */
        @JsonProperty("one_line")
        private String oneLine;
    }

    // Recommendation constants
    public static final String RECOMMEND_APPLY = "建议投递";
    public static final String RECOMMEND_NOT_APPLY = "不建议投递";
    public static final String RECOMMEND_UNCERTAIN = "信息不足，建议补充后再评估";

    /**
     * Create recommendation based on hard gate and score.
     */
    public static String createRecommendation(OverallGateStatus gateStatus, MatchLevel matchLevel) {
        if (gateStatus == OverallGateStatus.FAILED) {
            return RECOMMEND_NOT_APPLY;
        }
        if (gateStatus == OverallGateStatus.UNCERTAIN) {
            return RECOMMEND_UNCERTAIN;
        }
        // Gate passed
        if (matchLevel == MatchLevel.A || matchLevel == MatchLevel.B) {
            return RECOMMEND_APPLY;
        }
        if (matchLevel == MatchLevel.C) {
            return RECOMMEND_APPLY;  // Can try
        }
        return RECOMMEND_NOT_APPLY;  // Level D
    }

    /**
     * Get the overall score.
     */
    public int getOverallScore() {
        return summary != null ? summary.getOverallScore() : 0;
    }

    /**
     * Get the match level.
     */
    public MatchLevel getMatchLevel() {
        return scores != null ? scores.getMatchLevel() : MatchLevel.D;
    }

    /**
     * Check if recommendation is to apply.
     */
    public boolean shouldApply() {
        return summary != null && RECOMMEND_APPLY.equals(summary.getRecommendation());
    }
}
