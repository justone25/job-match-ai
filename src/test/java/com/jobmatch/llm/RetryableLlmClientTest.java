package com.jobmatch.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RetryableLlmClient.
 */
@ExtendWith(MockitoExtension.class)
class RetryableLlmClientTest {

    @Mock
    private LlmClient mockClient;

    private RetryableLlmClient retryableClient;

    @BeforeEach
    void setUp() {
        // Use short delay for tests
        retryableClient = new RetryableLlmClient(mockClient, 2, 10L, 1.5);
    }

    @Test
    void shouldSucceedOnFirstAttempt() throws LlmException {
        LlmResponse expectedResponse = LlmResponse.builder()
                .content("Success")
                .build();
        when(mockClient.chat(any())).thenReturn(expectedResponse);

        LlmRequest request = LlmRequest.of("Test");
        LlmResponse response = retryableClient.chat(request);

        assertEquals("Success", response.getContent());
        verify(mockClient, times(1)).chat(any());
    }

    @Test
    void shouldRetryOnConnectionFailure() throws LlmException {
        LlmResponse expectedResponse = LlmResponse.builder()
                .content("Success after retry")
                .build();

        when(mockClient.chat(any()))
                .thenThrow(new LlmException(LlmException.ErrorType.CONNECTION_FAILED, "Connection failed"))
                .thenReturn(expectedResponse);

        LlmRequest request = LlmRequest.of("Test");
        LlmResponse response = retryableClient.chat(request);

        assertEquals("Success after retry", response.getContent());
        verify(mockClient, times(2)).chat(any());
    }

    @Test
    void shouldRetryOnTimeout() throws LlmException {
        LlmResponse expectedResponse = LlmResponse.builder()
                .content("Success")
                .build();

        when(mockClient.chat(any()))
                .thenThrow(new LlmException(LlmException.ErrorType.REQUEST_TIMEOUT, "Timeout"))
                .thenThrow(new LlmException(LlmException.ErrorType.REQUEST_TIMEOUT, "Timeout"))
                .thenReturn(expectedResponse);

        LlmRequest request = LlmRequest.of("Test");
        LlmResponse response = retryableClient.chat(request);

        assertEquals("Success", response.getContent());
        verify(mockClient, times(3)).chat(any());
    }

    @Test
    void shouldNotRetryOnInvalidApiKey() throws LlmException {
        when(mockClient.chat(any()))
                .thenThrow(new LlmException(LlmException.ErrorType.INVALID_API_KEY, "Invalid key"));

        LlmRequest request = LlmRequest.of("Test");

        LlmException exception = assertThrows(LlmException.class, () -> retryableClient.chat(request));
        assertEquals(LlmException.ErrorType.INVALID_API_KEY, exception.getErrorType());
        verify(mockClient, times(1)).chat(any());
    }

    @Test
    void shouldNotRetryOnModelNotFound() throws LlmException {
        when(mockClient.chat(any()))
                .thenThrow(new LlmException(LlmException.ErrorType.MODEL_NOT_FOUND, "Model not found"));

        LlmRequest request = LlmRequest.of("Test");

        LlmException exception = assertThrows(LlmException.class, () -> retryableClient.chat(request));
        assertEquals(LlmException.ErrorType.MODEL_NOT_FOUND, exception.getErrorType());
        verify(mockClient, times(1)).chat(any());
    }

    @Test
    void shouldFailAfterMaxRetries() throws LlmException {
        when(mockClient.chat(any()))
                .thenThrow(new LlmException(LlmException.ErrorType.CONNECTION_FAILED, "Connection failed"));

        LlmRequest request = LlmRequest.of("Test");

        LlmException exception = assertThrows(LlmException.class, () -> retryableClient.chat(request));
        assertEquals(LlmException.ErrorType.CONNECTION_FAILED, exception.getErrorType());
        // Initial + 2 retries = 3 total
        verify(mockClient, times(3)).chat(any());
    }

    @Test
    void shouldRetryOnRateLimited() throws LlmException {
        LlmResponse expectedResponse = LlmResponse.builder()
                .content("Success")
                .build();

        when(mockClient.chat(any()))
                .thenThrow(new LlmException(LlmException.ErrorType.RATE_LIMITED, "Rate limited"))
                .thenReturn(expectedResponse);

        LlmRequest request = LlmRequest.of("Test");
        LlmResponse response = retryableClient.chat(request);

        assertEquals("Success", response.getContent());
        verify(mockClient, times(2)).chat(any());
    }

    @Test
    void shouldDelegateIsAvailable() {
        when(mockClient.isAvailable()).thenReturn(true);
        assertTrue(retryableClient.isAvailable());

        when(mockClient.isAvailable()).thenReturn(false);
        assertFalse(retryableClient.isAvailable());
    }

    @Test
    void shouldDelegateProviderName() {
        when(mockClient.getProviderName()).thenReturn("test-provider");
        assertEquals("test-provider", retryableClient.getProviderName());
    }

    @Test
    void shouldDelegateModelName() {
        when(mockClient.getModelName()).thenReturn("test-model");
        assertEquals("test-model", retryableClient.getModelName());
    }
}
