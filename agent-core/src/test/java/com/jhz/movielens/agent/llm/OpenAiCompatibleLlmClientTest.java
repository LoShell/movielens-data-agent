package com.jhz.movielens.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jhz.movielens.agent.core.AgentContext;
import com.jhz.movielens.agent.core.PromptBuilder;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.protocol.ToolResult;
import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolRegistry;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void callsOpenAiCompatibleEndpointAndParsesToolDecision() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = startServer(requestBody);
        try {
            ToolRegistry registry = new ToolRegistry();
            registry.register(new FakeTool());
            OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                    "test-key", "test-model",
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/v1"),
                    Duration.ofSeconds(5), 1000, 0.1,
                    Map.of(), HttpClient.newHttpClient(), objectMapper,
                    new PromptBuilder(), registry);
            AgentContext context = new AgentContext("task-1", "执行真实数据分析");
            context.putAttribute("taskId", "task-1");

            AgentResponse response = client.next(context);

            assertTrue(response.isToolCall());
            assertEquals("fake_tool", response.getToolCall().getTool());
            JsonNode sent = objectMapper.readTree(requestBody.get());
            assertEquals("test-model", sent.path("model").asText());
            assertEquals("json_object", sent.path("response_format").path("type").asText());
            assertEquals("system", sent.path("messages").path(0).path("role").asText());
            assertTrue(sent.path("messages").path(0).path("content").asText().contains("fake_tool"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void failsClearlyWhenApiKeyIsMissing() {
        OpenAiCompatibleLlmClient client = new OpenAiCompatibleLlmClient(
                "", "model", "https://example.invalid/v1",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 100, 0.1,
                Map.of(), new ToolRegistry());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.next(new AgentContext("task-1", "test")));

        assertTrue(exception.getMessage().contains("LLM_API_KEY"));
    }

    private HttpServer startServer(AtomicReference<String> requestBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String content = """
                    {"type":"TOOL_CALL","message":"use a registered tool",\
                    "toolCall":{"tool":"fake_tool","input":{"value":"ok"}},"summary":null}
                    """;
            String response = objectMapper.writeValueAsString(Map.of(
                    "choices", new Object[]{Map.of("message", Map.of("content", content))}));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static final class FakeTool implements Tool {
        @Override
        public String name() {
            return "fake_tool";
        }

        @Override
        public String description() {
            return "A fake registered tool.";
        }

        @Override
        public ToolResult execute(JsonNode input) {
            return ToolResult.success("ok", input == null ? JsonNodeFactory.instance.objectNode() : input);
        }
    }
}
