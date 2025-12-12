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
 * LLM client implementation for Ollama (local).
 * Uses Ollama's OpenAI-compatible API endpoint.
 */
public class OllamaClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final String model;
    private final int timeout;

    public OllamaClient(AppConfig.LocalLlmConfig config) {
        this.baseUrl = config.getBaseUrl();
        this.model = config.getModel();
        this.timeout = config.getTimeout();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        log.info("Initialized Ollama client: baseUrl={}, model={}", baseUrl, model);
    }

    @Override
    public LlmResponse chat(LlmRequest request) throws LlmException {
        long startTime = System.currentTimeMillis();

        try {
            // Build request body
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("stream", false);

            // Add messages
            ArrayNode messagesArray = requestBody.putArray("messages");
            for (LlmRequest.Message msg : request.getMessages()) {
                ObjectNode messageNode = messagesArray.addObject();
                messageNode.put("role", msg.getRole());
                messageNode.put("content", msg.getContent());
            }

            // Add options
            ObjectNode optionsNode = requestBody.putObject("options");
            optionsNode.put("temperature", request.getTemperature());
            optionsNode.put("num_predict", request.getMaxTokens());

            // Enable JSON mode if requested
            if (Boolean.TRUE.equals(request.getJsonMode())) {
                requestBody.put("format", "json");
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("Ollama request: {}", requestJson);

            // Build HTTP request
            Request httpRequest = new Request.Builder()
                    .url(baseUrl + "/api/chat")
                    .post(RequestBody.create(requestJson, JSON))
                    .build();

            // Execute request
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    handleErrorResponse(response);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                log.debug("Ollama response: {}", responseBody);

                return parseResponse(responseBody, startTime);
            }

        } catch (LlmException e) {
            throw e;
        } catch (java.net.SocketTimeoutException e) {
            throw new LlmException(LlmException.ErrorType.REQUEST_TIMEOUT,
                    "Ollama request timed out after " + timeout + " seconds", e);
        } catch (java.net.ConnectException e) {
            throw new LlmException(LlmException.ErrorType.CONNECTION_FAILED,
                    "Failed to connect to Ollama at " + baseUrl + ". Is Ollama running?", e);
        } catch (IOException e) {
            throw new LlmException(LlmException.ErrorType.CONNECTION_FAILED,
                    "Network error while calling Ollama: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new LlmException(LlmException.ErrorType.UNKNOWN,
                    "Unexpected error: " + e.getMessage(), e);
        }
    }

    private void handleErrorResponse(Response response) throws LlmException, IOException {
        int code = response.code();
        String body = response.body() != null ? response.body().string() : "";

        if (code == 404) {
            throw new LlmException(LlmException.ErrorType.MODEL_NOT_FOUND,
                    "Model '" + model + "' not found. Run: ollama pull " + model);
        } else if (code == 429) {
            throw new LlmException(LlmException.ErrorType.RATE_LIMITED,
                    "Rate limited by Ollama");
        } else {
            throw new LlmException(LlmException.ErrorType.UNKNOWN,
                    "Ollama returned error " + code + ": " + body);
        }
    }

    private LlmResponse parseResponse(String responseBody, long startTime) throws LlmException {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Extract content from message
            String content = "";
            JsonNode messageNode = root.get("message");
            if (messageNode != null && messageNode.has("content")) {
                content = messageNode.get("content").asText();
            }

            // Extract token counts (Ollama specific fields)
            int promptTokens = root.has("prompt_eval_count") ? root.get("prompt_eval_count").asInt() : 0;
            int completionTokens = root.has("eval_count") ? root.get("eval_count").asInt() : 0;

            return LlmResponse.builder()
                    .content(content)
                    .model(model)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .finishReason("stop")
                    .latencyMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            throw new LlmException(LlmException.ErrorType.INVALID_RESPONSE,
                    "Failed to parse Ollama response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/tags")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.debug("Ollama availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }

    @Override
    public String getModelName() {
        return model;
    }
}
