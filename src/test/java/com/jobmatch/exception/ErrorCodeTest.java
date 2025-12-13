package com.jobmatch.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorCode enum.
 */
class ErrorCodeTest {

    // ==================== Error Code Range Tests ====================

    @Test
    @DisplayName("Input errors should be in 1xxx range")
    void inputErrorsShouldBeIn1xxxRange() {
        assertTrue(ErrorCode.EMPTY_RESUME.getCode() >= 1000 && ErrorCode.EMPTY_RESUME.getCode() < 2000);
        assertTrue(ErrorCode.EMPTY_JD.getCode() >= 1000 && ErrorCode.EMPTY_JD.getCode() < 2000);
        assertTrue(ErrorCode.FILE_NOT_FOUND.getCode() >= 1000 && ErrorCode.FILE_NOT_FOUND.getCode() < 2000);
    }

    @Test
    @DisplayName("Parse errors should be in 2xxx range")
    void parseErrorsShouldBeIn2xxxRange() {
        assertTrue(ErrorCode.RESUME_PARSE_FAILED.getCode() >= 2000 && ErrorCode.RESUME_PARSE_FAILED.getCode() < 3000);
        assertTrue(ErrorCode.JD_PARSE_FAILED.getCode() >= 2000 && ErrorCode.JD_PARSE_FAILED.getCode() < 3000);
        assertTrue(ErrorCode.JSON_PARSE_ERROR.getCode() >= 2000 && ErrorCode.JSON_PARSE_ERROR.getCode() < 3000);
    }

    @Test
    @DisplayName("LLM errors should be in 3xxx range")
    void llmErrorsShouldBeIn3xxxRange() {
        assertTrue(ErrorCode.LLM_CONNECTION_FAILED.getCode() >= 3000 && ErrorCode.LLM_CONNECTION_FAILED.getCode() < 4000);
        assertTrue(ErrorCode.LLM_TIMEOUT.getCode() >= 3000 && ErrorCode.LLM_TIMEOUT.getCode() < 4000);
        assertTrue(ErrorCode.LLM_MODEL_NOT_FOUND.getCode() >= 3000 && ErrorCode.LLM_MODEL_NOT_FOUND.getCode() < 4000);
    }

    @Test
    @DisplayName("Storage errors should be in 4xxx range")
    void storageErrorsShouldBeIn4xxxRange() {
        assertTrue(ErrorCode.STORAGE_READ_FAILED.getCode() >= 4000 && ErrorCode.STORAGE_READ_FAILED.getCode() < 5000);
        assertTrue(ErrorCode.STORAGE_WRITE_FAILED.getCode() >= 4000 && ErrorCode.STORAGE_WRITE_FAILED.getCode() < 5000);
        assertTrue(ErrorCode.HISTORY_NOT_FOUND.getCode() >= 4000 && ErrorCode.HISTORY_NOT_FOUND.getCode() < 5000);
    }

    @Test
    @DisplayName("System errors should be in 5xxx range")
    void systemErrorsShouldBeIn5xxxRange() {
        assertTrue(ErrorCode.INTERNAL_ERROR.getCode() >= 5000);
        assertTrue(ErrorCode.CONFIG_LOAD_FAILED.getCode() >= 5000);
        assertTrue(ErrorCode.UNEXPECTED_ERROR.getCode() >= 5000);
    }

    // ==================== User Fixable Tests ====================

    @Test
    @DisplayName("Input errors should be user fixable")
    void inputErrorsShouldBeUserFixable() {
        assertTrue(ErrorCode.EMPTY_RESUME.isUserFixable());
        assertTrue(ErrorCode.EMPTY_JD.isUserFixable());
        assertTrue(ErrorCode.FILE_NOT_FOUND.isUserFixable());
        assertTrue(ErrorCode.INVALID_CONFIG.isUserFixable());
    }

    @Test
    @DisplayName("Non-input errors should not be user fixable")
    void nonInputErrorsShouldNotBeUserFixable() {
        assertFalse(ErrorCode.RESUME_PARSE_FAILED.isUserFixable());
        assertFalse(ErrorCode.LLM_CONNECTION_FAILED.isUserFixable());
        assertFalse(ErrorCode.INTERNAL_ERROR.isUserFixable());
    }

    // ==================== Retryable Tests ====================

    @Test
    @DisplayName("Certain errors should be retryable")
    void certainErrorsShouldBeRetryable() {
        assertTrue(ErrorCode.JSON_PARSE_ERROR.isRetryable());
        assertTrue(ErrorCode.LLM_TIMEOUT.isRetryable());
        assertTrue(ErrorCode.LLM_RESPONSE_INVALID.isRetryable());
        assertTrue(ErrorCode.LLM_RATE_LIMITED.isRetryable());
        assertTrue(ErrorCode.LLM_SERVICE_ERROR.isRetryable());
    }

    @Test
    @DisplayName("Most errors should not be retryable")
    void mostErrorsShouldNotBeRetryable() {
        assertFalse(ErrorCode.EMPTY_RESUME.isRetryable());
        assertFalse(ErrorCode.FILE_NOT_FOUND.isRetryable());
        assertFalse(ErrorCode.LLM_MODEL_NOT_FOUND.isRetryable());
        assertFalse(ErrorCode.INTERNAL_ERROR.isRetryable());
    }

    // ==================== Format Tests ====================

    @Test
    @DisplayName("Should format error message correctly")
    void shouldFormatErrorMessage() {
        String formatted = ErrorCode.EMPTY_RESUME.format();
        assertTrue(formatted.startsWith("[Error 1001]"));
        assertTrue(formatted.contains("简历内容为空"));
    }

    @Test
    @DisplayName("Should format with suggestion correctly")
    void shouldFormatWithSuggestion() {
        String formatted = ErrorCode.LLM_CONNECTION_FAILED.formatWithSuggestion();
        assertTrue(formatted.contains("[Error 3001]"));
        assertTrue(formatted.contains("LLM服务连接失败"));
        assertTrue(formatted.contains("建议:"));
        assertTrue(formatted.contains("Ollama"));
    }

    // ==================== fromCode Tests ====================

    @Test
    @DisplayName("Should find error code by code number")
    void shouldFindErrorCodeByNumber() {
        assertEquals(ErrorCode.EMPTY_RESUME, ErrorCode.fromCode(1001));
        assertEquals(ErrorCode.LLM_TIMEOUT, ErrorCode.fromCode(3002));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.fromCode(5001));
    }

    @Test
    @DisplayName("Should return UNEXPECTED_ERROR for unknown code")
    void shouldReturnUnexpectedErrorForUnknownCode() {
        assertEquals(ErrorCode.UNEXPECTED_ERROR, ErrorCode.fromCode(9999));
        assertEquals(ErrorCode.UNEXPECTED_ERROR, ErrorCode.fromCode(0));
        assertEquals(ErrorCode.UNEXPECTED_ERROR, ErrorCode.fromCode(-1));
    }

    // ==================== Suggestion Tests ====================

    @Test
    @DisplayName("All error codes should have suggestions")
    void allErrorCodesShouldHaveSuggestions() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getSuggestion(), "Error code " + code.name() + " should have a suggestion");
            assertFalse(code.getSuggestion().isEmpty(), "Error code " + code.name() + " suggestion should not be empty");
        }
    }

    @Test
    @DisplayName("All error codes should have messages")
    void allErrorCodesShouldHaveMessages() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.getMessage(), "Error code " + code.name() + " should have a message");
            assertFalse(code.getMessage().isEmpty(), "Error code " + code.name() + " message should not be empty");
        }
    }
}
