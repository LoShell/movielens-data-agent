package com.jhz.movielens.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jhz.movielens.agent.core.AgentContext;
import com.jhz.movielens.agent.core.PromptBuilder;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.tool.ToolRegistry;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleLlmClient implements LlmClient, TextLlmClient {
    private final String apiKey;
    private final String model;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final int maxTokens;
    private final double temperature;
    private final Map<String, Object> extraBody;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ToolRegistry toolRegistry;

    public OpenAiCompatibleLlmClient(String apiKey, String model, String baseUrl,
                                     Duration connectTimeout, Duration requestTimeout,
                                     int maxTokens, double temperature,
                                     Map<String, Object> extraBody, ToolRegistry toolRegistry) {
        this(apiKey, model, URI.create(baseUrl), requestTimeout, maxTokens, temperature,
                extraBody,
                HttpClient.newBuilder().connectTimeout(connectTimeout).build(),
                new ObjectMapper(), new PromptBuilder(), toolRegistry);
    }

    OpenAiCompatibleLlmClient(String apiKey, String model, URI baseUrl,
                              Duration requestTimeout, int maxTokens, double temperature,
                              Map<String, Object> extraBody, HttpClient httpClient,
                              ObjectMapper objectMapper, PromptBuilder promptBuilder,
                              ToolRegistry toolRegistry) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = requireText(model, "model");
        this.endpoint = chatCompletionsEndpoint(baseUrl);
        this.requestTimeout = requestTimeout;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.extraBody = extraBody == null ? Map.of() : Map.copyOf(extraBody);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public AgentResponse next(AgentContext context) {
        String systemPrompt = promptBuilder.buildSystemPrompt(context, toolRegistry);
        String userPrompt = promptBuilder.buildUserPrompt(context);
        String content = call(List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)), true);
        try {
            AgentResponse response = objectMapper.readValue(content, AgentResponse.class);
            if (response.getType() == null) {
                throw new IllegalStateException("LLM JSON response is missing type.");
            }
            return response;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("LLM returned invalid agent JSON: " + abbreviate(content), exception);
        }
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", requireText(systemPrompt, "systemPrompt")));
        messages.add(Map.of("role", "user", "content", requireText(userPrompt, "userPrompt")));
        return call(messages, false);
    }

    private String call(List<Map<String, String>> messages, boolean jsonResponse) {
        ensureConfigured();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            if (jsonResponse) {
                body.put("response_format", Map.of("type", "json_object"));
            }
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);
            body.putAll(extraBody);

            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return extractContent(response);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call the configured LLM API: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM API call was interrupted.", exception);
        }
    }

    private String extractContent(HttpResponse<String> response) throws JsonProcessingException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM API returned HTTP " + response.statusCode()
                    + ": " + abbreviate(response.body()));
        }
        JsonNode content = objectMapper.readTree(response.body())
                .path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new IllegalStateException("LLM API response does not contain choices[0].message.content.");
        }
        return content.asText().trim();
    }

    private void ensureConfigured() {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY is not configured; real Agent execution is unavailable.");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static URI chatCompletionsEndpoint(URI baseUrl) {
        String normalized = baseUrl.toString().replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat/completions");
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n]+", " ");
        return sanitized.length() <= 500 ? sanitized : sanitized.substring(0, 500) + "...";
    }
}
