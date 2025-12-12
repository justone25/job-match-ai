package com.jobmatch.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LlmRequest.
 */
class LlmRequestTest {

    @Test
    void shouldCreateSimpleRequest() {
        LlmRequest request = LlmRequest.of("Hello");

        assertNotNull(request);
        assertEquals(1, request.getMessages().size());
        assertEquals("user", request.getMessages().get(0).getRole());
        assertEquals("Hello", request.getMessages().get(0).getContent());
    }

    @Test
    void shouldCreateRequestWithSystemPrompt() {
        LlmRequest request = LlmRequest.of("You are helpful", "Hello");

        assertNotNull(request);
        assertEquals(2, request.getMessages().size());

        assertEquals("system", request.getMessages().get(0).getRole());
        assertEquals("You are helpful", request.getMessages().get(0).getContent());

        assertEquals("user", request.getMessages().get(1).getRole());
        assertEquals("Hello", request.getMessages().get(1).getContent());
    }

    @Test
    void shouldHaveDefaultValues() {
        LlmRequest request = LlmRequest.builder().build();

        assertEquals(0.1, request.getTemperature());
        assertEquals(4096, request.getMaxTokens());
        assertFalse(request.getJsonMode());
        assertNotNull(request.getMessages());
    }

    @Test
    void shouldBuildWithCustomValues() {
        LlmRequest request = LlmRequest.builder()
                .temperature(0.8)
                .maxTokens(2000)
                .jsonMode(true)
                .messages(List.of(
                        LlmRequest.Message.user("Test")
                ))
                .build();

        assertEquals(0.8, request.getTemperature());
        assertEquals(2000, request.getMaxTokens());
        assertTrue(request.getJsonMode());
        assertEquals(1, request.getMessages().size());
    }

    @Test
    void shouldCreateMessageWithFactoryMethods() {
        LlmRequest.Message systemMsg = LlmRequest.Message.system("System content");
        LlmRequest.Message userMsg = LlmRequest.Message.user("User content");
        LlmRequest.Message assistantMsg = LlmRequest.Message.assistant("Assistant content");

        assertEquals("system", systemMsg.getRole());
        assertEquals("System content", systemMsg.getContent());

        assertEquals("user", userMsg.getRole());
        assertEquals("User content", userMsg.getContent());

        assertEquals("assistant", assistantMsg.getRole());
        assertEquals("Assistant content", assistantMsg.getContent());
    }
}
