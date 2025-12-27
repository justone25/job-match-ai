package com.jobmatch.model.match;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Complete soft score calculation result.
 * Based on PRD v3.2 section 13.2.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SoftScoreResult {

    /**
     * Skill score (default weight: 60%).
     */
    @JsonProperty("skill_score")
    private ScoreDetail skillScore;

    /**
     * Experience score (default weight: 30%).
     */
    @JsonProperty("experience_score")
    private ScoreDetail experienceScore;

    /**
     * Bonus score (default weight: 10%).
     */
    @JsonProperty("bonus_score")
    private ScoreDetail bonusScore;

    /**
     * Overall score calculation.
     */
    private OverallScore overall;

    /**
     * Overall score container.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverallScore {
        /**
         * Formula used for calculation.
         */
        private String formula;

        /**
         * Detailed calculation steps.
         */
        private String calculation;

        /**
         * Final score (0-100).
         */
        @JsonProperty("final_score")
        private int finalScore;

        /**
         * Match level based on score.
         */
        @JsonProperty("match_level")
        private MatchLevel matchLevel;
    }

    /**
     * Get the final overall score.
     */
    public int getFinalScore() {
        return overall != null ? overall.getFinalScore() : 0;
    }

    /**
     * Get the match level.
     */
    public MatchLevel getMatchLevel() {
        return overall != null ? overall.getMatchLevel() : MatchLevel.D;
    }

    /**
     * Calculate overall score from dimension scores.
     */
    public static SoftScoreResult calculate(ScoreDetail skill, ScoreDetail experience, ScoreDetail bonus) {
        skill.calculateWeightedScore();
        experience.calculateWeightedScore();
        bonus.calculateWeightedScore();

        double total = skill.getWeightedScore() + experience.getWeightedScore() + bonus.getWeightedScore();
        int finalScore = (int) Math.round(total);

        String formula = String.format("skill*%.1f + experience*%.1f + bonus*%.1f",
                skill.getWeight(), experience.getWeight(), bonus.getWeight());
        String calculation = String.format("%d*%.1f + %d*%.1f + %d*%.1f = %.1f",
                skill.getScore(), skill.getWeight(),
                experience.getScore(), experience.getWeight(),
                bonus.getScore(), bonus.getWeight(),
                total);

        return SoftScoreResult.builder()
                .skillScore(skill)
                .experienceScore(experience)
                .bonusScore(bonus)
                .overall(OverallScore.builder()
                        .formula(formula)
                        .calculation(calculation)
                        .finalScore(finalScore)
                        .matchLevel(MatchLevel.fromScore(finalScore))
                        .build())
                .build();
    }
}
