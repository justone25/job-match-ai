package com.jobmatch.llm;

import lombok.Builder;
import lombok.Data;

/**
 * Response object from LLM chat completion.
 */
@Data
@Builder
public class LlmResponse {

    /**
     * The generated text content.
     */
    private String content;

    /**
     * Number of tokens in the prompt.
     */
    private Integer promptTokens;

    /**
     * Number of tokens in the completion.
     */
    private Integer completionTokens;

    /**
     * Total tokens used.
     */
    private Integer totalTokens;

    /**
     * Model used for generation.
     */
    private String model;

    /**
     * Finish reason: stop, length, etc.
     */
    private String finishReason;

    /**
     * Response latency in milliseconds.
     */
    private Long latencyMs;

    /**
     * Whether the response was from cache.
     */
    @Builder.Default
    private Boolean fromCache = false;
}
