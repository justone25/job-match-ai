package com.jobmatch.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Metadata for parsing results.
 * Tracks when and how content was parsed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParseMeta {

    /**
     * When the parsing was performed.
     */
    @JsonProperty("parse_time")
    private Instant parseTime;

    /**
     * Version of the parser/model used.
     */
    @JsonProperty("model_version")
    @Builder.Default
    private String modelVersion = "v0.1";

    /**
     * Overall confidence score (0.0-1.0).
     */
    @JsonProperty("overall_confidence")
    @Builder.Default
    private Double overallConfidence = 0.0;

    /**
     * LLM provider used (local/cloud).
     */
    @JsonProperty("llm_provider")
    private String llmProvider;

    /**
     * Total tokens consumed.
     */
    @JsonProperty("total_tokens")
    private Integer totalTokens;

    /**
     * Latency in milliseconds.
     */
    @JsonProperty("latency_ms")
    private Long latencyMs;

    /**
     * Whether result was from cache.
     */
    @JsonProperty("from_cache")
    @Builder.Default
    private Boolean fromCache = false;
}
