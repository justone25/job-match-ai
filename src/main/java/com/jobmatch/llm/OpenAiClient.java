package com.jobmatch.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobmatch.config.AppConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * LLM client implementation for OpenAI-compatible APIs.
 * Supports OpenAI, Azure OpenAI, DeepSeek, and other compatible services.
 */
public class OpenAiClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);
    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int timeout;

    public OpenAiClient(AppConfig.CloudLlmConfig config) {
        this.baseUrl = normalizeBaseUrl(config.getBaseUrl());
        this.model = config.getModel();
        this.apiKey = resolveApiKey(config.getApiKey());
        this.timeout = config.getTimeout();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        log.info("Initialized OpenAI client: baseUrl={}, model={}", baseUrl, model);
    }

    /**
     * Normalize base URL - remove trailing slash if present.
     */
    private String normalizeBaseUrl(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Resolve API key from environment variable if needed.
     */
    private String resolveApiKey(String key) {
        if (key != null && key.startsWith("${") && key.endsWith("}")) {
            String envName = key.substring(2, key.length() - 1);
            String envValue = System.getenv(envName);
            if (envValue != null && !envValue.isEmpty()) {
                return envValue;
            }
            log.warn("Environment variable {} not set", envName);
            return null;
        }
        return key;
    }

    @Override
    public LlmResponse chat(LlmRequest request) throws LlmException {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new LlmException(LlmException.ErrorType.INVALID_API_KEY,
                    "API key not configured. Set LLM_API_KEY environment variable.");
        }

        long startTime = System.currentTimeMillis();

        try {
            // Build request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", request.getTemperature());
            requestBody.put("max_tokens", request.getMaxTokens());
            requestBody.put("stream", false);

            // Add messages
            ArrayNode messagesArray = requestBody.putArray("messages");
            for (LlmRequest.Message msg : request.getMessages()) {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
            }

            // Enable JSON mode if requested
            if (Boolean.TRUE.equals(request.getJsonMode())) {
                ObjectNode responseFormat = requestBody.putObject("response_format");
                responseFormat.put("type", "json_object");
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("OpenAI request: {}", requestJson);

            // Build HTTP request
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestJson, JSON))
                    .build();

            // Execute request
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                log.debug("OpenAI response: {}", responseBody);

                return parseResponse(responseBody, startTime);
            }

        } catch (LlmException e) {
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            throw new LlmException(LlmException.ErrorType.REQUEST_TIMEOUT,
                    "OpenAI request timed out after " + timeout + " seconds", e);
        } catch (java.net.ConnectException e) {
            throw new LlmException(LlmException.ErrorType.CONNECTION_FAILED,
                    "Failed to connect to OpenAI API at " + baseUrl, e);
        } catch (IOException e) {
            throw new LlmException(LlmException.ErrorType.CONNECTION_FAILED,
                    "Network error while calling OpenAI API: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new LlmException(LlmException.ErrorType.UNKNOWN,
                    "Unexpected error: " + e.getMessage(), e);
        }
    }

    private void handleErrorResponse(Response response) throws LlmException, IOException {
        int code = response.code();
        String body = response.body() != null ? response.body().string() : "";

        // Try to extract error message from response body
        String errorMessage = body;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error") && root.get("error").has("message")) {
                errorMessage = root.get("error").get("message").asText();
            }
        } catch (Exception ignored) {
            // Use raw body as error message
        }

        switch (code) {
            case 401:
                throw new LlmException(LlmException.ErrorType.INVALID_API_KEY,
                        "Invalid API key: " + errorMessage);
            case 403:
                throw new LlmException(LlmException.ErrorType.INVALID_API_KEY,
                        "Access denied: " + errorMessage);
            case 404:
                throw new LlmException(LlmException.ErrorType.MODEL_NOT_FOUND,
                        "Model '" + model + "' not found: " + errorMessage);
            case 429:
                throw new LlmException(LlmException.ErrorType.RATE_LIMITED,
                        "Rate limited: " + errorMessage);
            case 500:
            case 502:
            case 503:
                throw new LlmException(LlmException.ErrorType.CONNECTION_FAILED,
                        "OpenAI service error (" + code + "): " + errorMessage);
            default:
                throw new LlmException(LlmException.ErrorType.UNKNOWN,
                        "OpenAI API error (" + code + "): " + errorMessage);
        }
    }

    private LlmResponse parseResponse(String responseBody, long startTime) throws LlmException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Extract content from choices[0].message.content
            String content = "";
            String finishReason = "stop";
            JsonNode choicesNode = root.get("choices");
            if (choicesNode != null && choicesNode.isArray() && choicesNode.size() > 0) {
                JsonNode firstChoice = choicesNode.get(0);
                if (firstChoice.has("message") && firstChoice.get("message").has("content")) {
                    content = firstChoice.get("message").get("content").asText();
                }
                if (firstChoice.has("finish_reason")) {
                    finishReason = firstChoice.get("finish_reason").asText();
                }
            }

            // Extract token counts from usage
            int promptTokens = 0;
            int completionTokens = 0;
            int totalTokens = 0;
            JsonNode usageNode = root.get("usage");
            if (usageNode != null) {
                promptTokens = usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0;
                completionTokens = usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0;
                totalTokens = usageNode.has("total_tokens") ? usageNode.get("total_tokens").asInt() : 0;
            }

            // Extract model from response (may differ from request)
            String responseModel = root.has("model") ? root.get("model").asText() : model;

            return LlmResponse.builder()
                    .content(content)
                    .model(responseModel)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(totalTokens)
                    .finishReason(finishReason)
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            throw new LlmException(LlmException.ErrorType.INVALID_RESPONSE,
                    "Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        // Check if API key is configured
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        // Try to make a simple models list request
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/models")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.debug("OpenAI availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getModelName() {
        return model;
    }
}
