package com.jhz.movielens.agent.core;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jhz.movielens.agent.llm.LlmClient;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.protocol.AgentResponseType;
import com.jhz.movielens.agent.protocol.ToolCall;
import com.jhz.movielens.agent.protocol.ToolResult;
import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopTest {
    @Test
    void executesRegisteredToolAndReturnsDone() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());

        Queue<AgentResponse> responses = new ArrayDeque<>(List.of(
                AgentResponse.toolCall("Run the registered tool.", new ToolCall(
                        "echo", JsonNodeFactory.instance.objectNode().put("value", "hello"))),
                AgentResponse.done("The real tool result was observed.")
        ));
        LlmClient client = context -> responses.remove();
        AgentContext context = new AgentContext("task-1", "Evaluate the dataset.");

        AgentResponse response = new AgentLoop(client, registry, 3).run(context);

        assertEquals(AgentResponseType.DONE, response.getType());
        assertEquals("The real tool result was observed.", response.getSummary());
        assertTrue(context.getHistory().stream().anyMatch(message ->
                message.role() == AgentMessage.Role.TOOL
                        && message.content().contains("hello")));
    }

    @Test
    void recordsUnknownToolWithoutInventingAResult() {
        ToolRegistry registry = new ToolRegistry();
        Queue<AgentResponse> responses = new ArrayDeque<>(List.of(
                AgentResponse.toolCall("Call missing tool.", new ToolCall(
                        "missing", JsonNodeFactory.instance.objectNode())),
                AgentResponse.done("Failure was recorded.")
        ));
        AgentContext context = new AgentContext("task-2", "Run a missing tool.");

        AgentResponse response = new AgentLoop(ignored -> responses.remove(), registry, 3).run(context);

        assertEquals(AgentResponseType.DONE, response.getType());
        assertTrue(context.getHistory().stream().anyMatch(message ->
                message.role() == AgentMessage.Role.TOOL
                        && message.content().contains("UNKNOWN_TOOL")));
    }

    @Test
    void failsAfterMaximumSteps() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        LlmClient client = ignored -> AgentResponse.toolCall("Keep calling.", new ToolCall(
                "echo", JsonNodeFactory.instance.objectNode().put("value", "again")));

        AgentResponse response = new AgentLoop(client, registry, 2)
                .run(new AgentContext("task-3", "Do not loop forever."));

        assertEquals(AgentResponseType.FAILED, response.getType());
        assertTrue(response.getSummary().contains("maximum number of steps"));
    }

    private static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "Returns its structured input for testing.";
        }

        @Override
        public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
            return ToolResult.success("Echo completed.", input);
        }
    }
}
