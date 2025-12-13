package com.jobmatch.model.match;

/**
 * Overall status of hard gate check.
 * Based on PRD v3.2 section 13.2.1.
 */
public enum OverallGateStatus {
    /**
     * All gates passed (or only borderline).
     * Can proceed to soft scoring and recommendation.
     */
    PASSED,

    /**
     * At least one gate failed.
     * Not recommended to apply (unless clear path to remedy).
     */
    FAILED,

    /**
     * Information insufficient to determine.
     * Cannot give definitive recommendation.
     */
    UNCERTAIN
}
