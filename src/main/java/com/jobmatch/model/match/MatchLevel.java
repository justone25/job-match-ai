package com.jobmatch.model.match;

/**
 * Match level based on overall score.
 * Based on PRD v3.2 section 13.2.2.
 */
public enum MatchLevel {
    /**
     * A级 (90-100): High match, strongly recommend.
     */
    A("高度匹配，强烈推荐投递", 90, 100),

    /**
     * B级 (75-89): Good match, recommend.
     */
    B("较好匹配，建议投递", 75, 89),

    /**
     * C级 (60-74): Basic match, can try.
     */
    C("基本匹配，可以尝试", 60, 74),

    /**
     * D级 (<60): Low match, consider carefully.
     */
    D("匹配度低，谨慎考虑", 0, 59);

    private final String description;
    private final int minScore;
    private final int maxScore;

    MatchLevel(String description, int minScore, int maxScore) {
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDescription() {
        return description;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    /**
     * Get match level from score.
     */
    public static MatchLevel fromScore(int score) {
        if (score >= 90) return A;
        if (score >= 75) return B;
        if (score >= 60) return C;
        return D;
    }
}
