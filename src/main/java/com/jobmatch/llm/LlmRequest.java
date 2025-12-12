package com.jobmatch.llm;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Request object for LLM chat completion.
 */
@Data
@Builder
public class LlmRequest {

    /**
     * List of messages in the conversation.
     */
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    /**
     * Temperature for response generation (0.0 - 2.0).
     * Lower values make output more focused and deterministic.
     */
    @Builder.Default
    private Double temperature = 0.1;

    /**
     * Maximum tokens to generate.
     */
    @Builder.Default
    private Integer maxTokens = 4096;

    /**
     * Whether to enable JSON mode (forces JSON output).
     */
    @Builder.Default
    private Boolean jsonMode = false;

    @Data
    @Builder
    public static class Message {
        /**
         * Role: system, user, or assistant.
         */
        private String role;

        /**
         * Message content.
         */
        private String content;

        public static Message system(String content) {
            return Message.builder().role("system").content(content).build();
        }

        public static Message user(String content) {
            return Message.builder().role("user").content(content).build();
        }

        public static Message assistant(String content) {
            return Message.builder().role("assistant").content(content).build();
        }
    }

    /**
     * Create a simple request with a single user message.
     */
    public static LlmRequest of(String prompt) {
        return LlmRequest.builder()
                .messages(List.of(Message.user(prompt)))
                .build();
    }

    /**
     * Create a request with system prompt and user message.
     */
    public static LlmRequest of(String systemPrompt, String userMessage) {
        return LlmRequest.builder()
                .messages(List.of(
                        Message.system(systemPrompt),
                        Message.user(userMessage)
                ))
                .build();
    }
}
