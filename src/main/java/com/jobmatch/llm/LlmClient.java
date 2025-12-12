package com.jobmatch.llm;

/**
 * Unified interface for LLM clients.
 * Supports both local (Ollama) and cloud (OpenAI compatible) providers.
 */
public interface LlmClient {

    /**
     * Send a chat request to the LLM.
     *
     * @param request The chat request containing messages and options
     * @return The LLM response
     * @throws LlmException if the request fails
     */
    LlmResponse chat(LlmRequest request) throws LlmException;

    /**
     * Check if the LLM service is available.
     *
     * @return true if the service is reachable and ready
     */
    boolean isAvailable();

    /**
     * Get the provider name (e.g., "ollama", "openai").
     *
     * @return provider name
     */
    String getProviderName();

    /**
     * Get the model name being used.
     *
     * @return model name
     */
    String getModelName();
}
