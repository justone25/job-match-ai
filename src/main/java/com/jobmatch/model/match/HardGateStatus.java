package com.jobmatch.model.match;

/**
 * Status of hard gate requirement check.
 * Based on PRD v3.2 section 13.2.1.
 */
public enum HardGateStatus {
    /**
     * Requirement satisfied.
     */
    PASS,

    /**
     * Requirement not satisfied.
     */
    FAIL,

    /**
     * Close but slightly short (e.g., requires 5 years, has 4.5 years).
     */
    BORDERLINE,

    /**
     * Insufficient information to determine.
     */
    UNKNOWN
}
