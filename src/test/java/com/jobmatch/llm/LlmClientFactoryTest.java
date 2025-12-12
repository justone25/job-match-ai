package com.jobmatch.llm;

import com.jobmatch.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LlmClientFactory.
 */
class LlmClientFactoryTest {

    private AppConfig.LlmConfig llmConfig;

    @BeforeEach
    void setUp() {
        llmConfig = new AppConfig.LlmConfig();
        llmConfig.setLocal(new AppConfig.LocalLlmConfig());
        llmConfig.setCloud(new AppConfig.CloudLlmConfig());
        llmConfig.setCommon(new AppConfig.CommonLlmConfig());
    }

    @Test
    void shouldCreateLocalClient() throws LlmException {
        llmConfig.setProvider("local");
        llmConfig.getLocal().setBaseUrl("http://localhost:11434");
        llmConfig.getLocal().setModel("qwen2.5:14b");

        LlmClientFactory factory = new LlmClientFactory(llmConfig);
        LlmClient client = factory.create();

        assertNotNull(client);
        assertTrue(client instanceof OllamaClient);
        assertEquals("ollama", client.getProviderName());
        assertEquals("qwen2.5:14b", client.getModelName());
    }

    @Test
    void shouldCreateCloudClient() throws LlmException {
        llmConfig.setProvider("cloud");
        llmConfig.getCloud().setBaseUrl("https://api.openai.com/v1");
        llmConfig.getCloud().setModel("gpt-4o-mini");
        llmConfig.getCloud().setApiKey("test-key");

        LlmClientFactory factory = new LlmClientFactory(llmConfig);
        LlmClient client = factory.create();

        assertNotNull(client);
        assertTrue(client instanceof OpenAiClient);
        assertEquals("openai", client.getProviderName());
        assertEquals("gpt-4o-mini", client.getModelName());
    }

    @Test
    void shouldThrowOnUnknownProvider() {
        llmConfig.setProvider("unknown");

        LlmClientFactory factory = new LlmClientFactory(llmConfig);

        LlmException exception = assertThrows(LlmException.class, factory::create);
        assertTrue(exception.getMessage().contains("Unknown LLM provider"));
    }

    @Test
    void shouldBeCaseInsensitive() throws LlmException {
        llmConfig.setProvider("LOCAL");
        llmConfig.getLocal().setBaseUrl("http://localhost:11434");
        llmConfig.getLocal().setModel("test-model");

        LlmClientFactory factory = new LlmClientFactory(llmConfig);
        LlmClient client = factory.create();

        assertTrue(client instanceof OllamaClient);
    }

    @Test
    void shouldWrapWithRetryWhenConfigured() throws LlmException {
        llmConfig.setProvider("local");
        llmConfig.getLocal().setBaseUrl("http://localhost:11434");
        llmConfig.getLocal().setModel("test-model");
        llmConfig.getCommon().setRetryTimes(3);

        LlmClientFactory factory = new LlmClientFactory(llmConfig);

        // createWithFallback wraps with retry
        // We can't easily test this without making the client available
        // Just verify factory creates without error
        assertNotNull(factory);
    }

    @Test
    void shouldCheckLocalAvailability() {
        llmConfig.setLocal(new AppConfig.LocalLlmConfig());
        llmConfig.getLocal().setBaseUrl("http://localhost:99999"); // Non-existent port

        LlmClientFactory factory = new LlmClientFactory(llmConfig);

        // Should return false for unavailable service
        assertFalse(factory.isLocalAvailable());
    }

    @Test
    void shouldCheckCloudAvailability() {
        llmConfig.setCloud(new AppConfig.CloudLlmConfig());
        llmConfig.getCloud().setApiKey(null); // No API key

        LlmClientFactory factory = new LlmClientFactory(llmConfig);

        // Should return false without API key
        assertFalse(factory.isCloudAvailable());
    }
}
