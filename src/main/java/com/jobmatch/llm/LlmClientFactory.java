package com.jobmatch.llm;

import com.jobmatch.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating LLM clients based on configuration.
 * Supports automatic fallback between local and cloud providers.
 */
public class LlmClientFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmClientFactory.class);

    public static final String PROVIDER_LOCAL = "local";
    public static final String PROVIDER_CLOUD = "cloud";

    private final AppConfig.LlmConfig config;

    public LlmClientFactory(AppConfig.LlmConfig config) {
        this.config = config;
    }

    /**
     * Create LLM client based on configured provider.
     *
     * @return LlmClient instance
     * @throws LlmException if no valid provider can be created
     */
    public LlmClient create() throws LlmException {
        String provider = config.getProvider();
        log.debug("Creating LLM client for provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case PROVIDER_LOCAL -> createLocalClient();
            case PROVIDER_CLOUD -> createCloudClient();
            default -> throw new LlmException(LlmException.ErrorType.UNKNOWN,
                    "Unknown LLM provider: " + provider + ". Use 'local' or 'cloud'.");
        };
    }

    /**
     * Create LLM client with automatic fallback.
     * Tries primary provider first, falls back to secondary if unavailable.
     *
     * @return LlmClient instance
     * @throws LlmException if no provider is available
     */
    public LlmClient createWithFallback() throws LlmException {
        String primary = config.getProvider();
        String fallback = PROVIDER_LOCAL.equals(primary) ? PROVIDER_CLOUD : PROVIDER_LOCAL;

        // Try primary provider
        try {
            LlmClient primaryClient = createClientForProvider(primary);
            if (primaryClient.isAvailable()) {
                log.info("Using primary LLM provider: {} ({})",
                        primaryClient.getProviderName(), primaryClient.getModelName());
                return wrapWithRetry(primaryClient);
            }
            log.warn("Primary LLM provider '{}' not available, trying fallback", primary);
        } catch (Exception e) {
            log.warn("Failed to create primary LLM client: {}", e.getMessage());
        }

        // Try fallback provider
        try {
            LlmClient fallbackClient = createClientForProvider(fallback);
            if (fallbackClient.isAvailable()) {
                log.info("Using fallback LLM provider: {} ({})",
                        fallbackClient.getProviderName(), fallbackClient.getModelName());
                return wrapWithRetry(fallbackClient);
            }
        } catch (Exception e) {
            log.warn("Failed to create fallback LLM client: {}", e.getMessage());
        }

        throw new LlmException(LlmException.ErrorType.CONNECTION_FAILED,
                "No LLM provider available. Check your configuration and ensure Ollama is running or API key is set.");
    }

    /**
     * Create client for specific provider.
     */
    private LlmClient createClientForProvider(String provider) throws LlmException {
        return switch (provider.toLowerCase()) {
            case PROVIDER_LOCAL -> createLocalClient();
            case PROVIDER_CLOUD -> createCloudClient();
            default -> throw new LlmException(LlmException.ErrorType.UNKNOWN,
                    "Unknown provider: " + provider);
        };
    }

    /**
     * Create local Ollama client.
     */
    private LlmClient createLocalClient() {
        return new OllamaClient(config.getLocal());
    }

    /**
     * Create cloud OpenAI client.
     */
    private LlmClient createCloudClient() {
        return new OpenAiClient(config.getCloud());
    }

    /**
     * Wrap client with retry mechanism.
     */
    private LlmClient wrapWithRetry(LlmClient client) {
        int retryTimes = config.getCommon().getRetryTimes();
        if (retryTimes > 0) {
            return new RetryableLlmClient(client, retryTimes);
        }
        return client;
    }

    /**
     * Check if local provider is available.
     */
    public boolean isLocalAvailable() {
        try {
            return createLocalClient().isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if cloud provider is available.
     */
    public boolean isCloudAvailable() {
        try {
            return createCloudClient().isAvailable();
        } catch (Exception e) {
            return false;
        }
    }
}
