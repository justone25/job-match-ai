package com.jobmatch.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Decorator that adds retry capability to LLM clients.
 * Implements exponential backoff strategy for transient failures.
 */
public class RetryableLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(RetryableLlmClient.class);

    /**
     * Error types that are retryable (transient failures).
     */
    private static final Set<LlmException.ErrorType> RETRYABLE_ERRORS = Set.of(
            LlmException.ErrorType.CONNECTION_FAILED,
            LlmException.ErrorType.REQUEST_TIMEOUT,
            LlmException.ErrorType.RATE_LIMITED
    );

    private final LlmClient delegate;
    private final int maxRetries;
    private final long initialDelayMs;
    private final double backoffMultiplier;

    /**
     * Create retryable client with default backoff settings.
     *
     * @param delegate   underlying LLM client
     * @param maxRetries maximum number of retry attempts
     */
    public RetryableLlmClient(LlmClient delegate, int maxRetries) {
        this(delegate, maxRetries, 1000L, 2.0);
    }

    /**
     * Create retryable client with custom backoff settings.
     *
     * @param delegate          underlying LLM client
     * @param maxRetries        maximum number of retry attempts
     * @param initialDelayMs    initial delay before first retry (ms)
     * @param backoffMultiplier multiplier for subsequent delays
     */
    public RetryableLlmClient(LlmClient delegate, int maxRetries, long initialDelayMs, double backoffMultiplier) {
        this.delegate = delegate;
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
    }

    @Override
    public LlmResponse chat(LlmRequest request) throws LlmException {
        LlmException lastException = null;
        int attempt = 0;

        while (attempt <= maxRetries) {
            try {
                LlmResponse response = delegate.chat(request);
                if (attempt > 0) {
                    log.info("LLM request succeeded after {} retries", attempt);
                }
                return response;
            } catch (LlmException e) {
                lastException = e;

                if (!isRetryable(e)) {
                    log.debug("Non-retryable error: {} - {}", e.getErrorType(), e.getMessage());
                    throw e;
                }

                if (attempt < maxRetries) {
                    long delay = calculateDelay(attempt);
                    log.warn("LLM request failed (attempt {}/{}): {} - {}. Retrying in {}ms...",
                            attempt + 1, maxRetries + 1, e.getErrorType(), e.getMessage(), delay);
                    sleep(delay);
                } else {
                    log.error("LLM request failed after {} attempts: {} - {}",
                            maxRetries + 1, e.getErrorType(), e.getMessage());
                }

                attempt++;
            }
        }

        throw lastException;
    }

    /**
     * Check if the error is retryable.
     */
    private boolean isRetryable(LlmException e) {
        return RETRYABLE_ERRORS.contains(e.getErrorType());
    }

    /**
     * Calculate delay for exponential backoff.
     */
    private long calculateDelay(int attempt) {
        return (long) (initialDelayMs * Math.pow(backoffMultiplier, attempt));
    }

    /**
     * Sleep for specified duration.
     */
    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }
}
