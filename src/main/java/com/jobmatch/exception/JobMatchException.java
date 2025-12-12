package com.jobmatch.exception;

/**
 * Base exception for JobMatch application.
 * Uses error code system:
 * - 1xxx: Input errors
 * - 2xxx: Parse errors
 * - 3xxx: LLM errors
 * - 4xxx: Storage errors
 * - 5xxx: System errors
 */
public class JobMatchException extends RuntimeException {

    private final int errorCode;

    public JobMatchException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JobMatchException(int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Get formatted error message with code.
     */
    public String getFormattedMessage() {
        return String.format("[Error %d] %s", errorCode, getMessage());
    }
}
