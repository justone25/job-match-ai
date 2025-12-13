package com.jobmatch.exception;

/**
 * Base exception for JobMatch application.
 * Uses standardized error codes with suggestions.
 */
public class JobMatchException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public JobMatchException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public JobMatchException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage() + (detail != null ? ": " + detail : ""));
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public JobMatchException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = cause.getMessage();
    }

    public JobMatchException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage() + (detail != null ? ": " + detail : ""), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * Constructor with raw error code (for backward compatibility).
     */
    public JobMatchException(int code, String message) {
        super(message);
        this.errorCode = ErrorCode.fromCode(code);
        this.detail = message;
    }

    /**
     * Constructor with raw error code (for backward compatibility).
     */
    public JobMatchException(int code, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.fromCode(code);
        this.detail = message;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    public String getDetail() {
        return detail;
    }

    public String getSuggestion() {
        return errorCode.getSuggestion();
    }

    /**
     * Check if user can fix this error.
     */
    public boolean isUserFixable() {
        return errorCode.isUserFixable();
    }

    /**
     * Check if this error is retryable.
     */
    public boolean isRetryable() {
        return errorCode.isRetryable();
    }

    /**
     * Get formatted error message with code.
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[Error ").append(errorCode.getCode()).append("] ");
        sb.append(errorCode.getMessage());
        if (detail != null && !detail.equals(errorCode.getMessage())) {
            sb.append(": ").append(detail);
        }
        return sb.toString();
    }

    /**
     * Get formatted message with suggestion for display to user.
     */
    public String getUserFriendlyMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("❌ ").append(getFormattedMessage()).append("\n");
        sb.append("\n");
        sb.append("💡 建议: ").append(errorCode.getSuggestion()).append("\n");

        if (isRetryable()) {
            sb.append("   (此错误可能通过重试解决)\n");
        }

        return sb.toString();
    }

    /**
     * Create exception for input errors.
     */
    public static JobMatchException inputError(ErrorCode code, String detail) {
        if (code.getCode() < 1000 || code.getCode() >= 2000) {
            throw new IllegalArgumentException("Input error code must be in 1xxx range");
        }
        return new JobMatchException(code, detail);
    }

    /**
     * Create exception for parse errors.
     */
    public static JobMatchException parseError(ErrorCode code, String detail) {
        if (code.getCode() < 2000 || code.getCode() >= 3000) {
            throw new IllegalArgumentException("Parse error code must be in 2xxx range");
        }
        return new JobMatchException(code, detail);
    }

    /**
     * Create exception for LLM errors.
     */
    public static JobMatchException llmError(ErrorCode code, String detail) {
        if (code.getCode() < 3000 || code.getCode() >= 4000) {
            throw new IllegalArgumentException("LLM error code must be in 3xxx range");
        }
        return new JobMatchException(code, detail);
    }

    /**
     * Create exception for storage errors.
     */
    public static JobMatchException storageError(ErrorCode code, String detail) {
        if (code.getCode() < 4000 || code.getCode() >= 5000) {
            throw new IllegalArgumentException("Storage error code must be in 4xxx range");
        }
        return new JobMatchException(code, detail);
    }
}
