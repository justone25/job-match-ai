package com.jobmatch.llm;

import com.jobmatch.exception.JobMatchException;

/**
 * Exception for LLM-related errors.
 */
public class LlmException extends JobMatchException {

    public enum ErrorType {
        CONNECTION_FAILED(3001, "LLM service connection failed"),
        INVALID_API_KEY(3002, "Invalid or missing API key"),
        REQUEST_TIMEOUT(3003, "LLM request timeout"),
        INVALID_RESPONSE(3004, "Invalid response format"),
        QUOTA_EXCEEDED(3005, "API quota exceeded"),
        MODEL_NOT_FOUND(3006, "Model not found or not installed"),
        CONTENT_FILTERED(3007, "Content filtered by moderation"),
        RATE_LIMITED(3008, "Rate limited, please retry later"),
        UNKNOWN(3099, "Unknown LLM error");

        private final int code;
        private final String description;

        ErrorType(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public int getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }
    }

    private final ErrorType errorType;

    public LlmException(ErrorType errorType) {
        super(errorType.getCode(), errorType.getDescription());
        this.errorType = errorType;
    }

    public LlmException(ErrorType errorType, String message) {
        super(errorType.getCode(), message);
        this.errorType = errorType;
    }

    public LlmException(ErrorType errorType, String message, Throwable cause) {
        super(errorType.getCode(), message, cause);
        this.errorType = errorType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }
}
