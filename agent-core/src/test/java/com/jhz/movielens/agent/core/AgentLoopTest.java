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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

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
    void publishesPlanningActionObservationAndFinalEvents() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        Queue<AgentResponse> responses = new ArrayDeque<>(List.of(
                AgentResponse.toolCall("Run echo.", new ToolCall(
                        "echo", JsonNodeFactory.instance.objectNode().put("value", "hello"))),
                AgentResponse.done("Finished.")));
        List<AgentEvent> events = new ArrayList<>();

        AgentResponse response = new AgentLoop(ignored -> responses.remove(), registry, 3, events::add)
                .run(new AgentContext("task-events", "Test progress events."));

        assertEquals(AgentResponseType.DONE, response.getType());
        assertEquals(List.of(
                        AgentEvent.Type.PLAN,
                        AgentEvent.Type.ACTION,
                        AgentEvent.Type.OBSERVATION,
                        AgentEvent.Type.PLAN,
                        AgentEvent.Type.FINAL),
                events.stream().map(AgentEvent::type).toList());
        assertEquals("echo", events.get(1).toolName());
        assertEquals(100, events.get(events.size() - 1).progress());
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

    @Test
    void neverExecutesAnAlgorithmOutsideTheExplicitSelection() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        AtomicInteger otherCalls = new AtomicInteger();
        registry.register(new CountingTool(otherCalls));
        Queue<AgentResponse> responses = new ArrayDeque<>(List.of(
                AgentResponse.toolCall("Try another tool.", new ToolCall(
                        "other", JsonNodeFactory.instance.objectNode())),
                AgentResponse.toolCall("Use the selected tool.", new ToolCall(
                        "echo", JsonNodeFactory.instance.objectNode())),
                AgentResponse.done("Selected result only.")));
        AgentContext context = new AgentContext("task-selected", "Use echo only.");
        context.putAttribute("requestedTools", "echo");

        AgentResponse response = new AgentLoop(ignored -> responses.remove(), registry, 4).run(context);

        assertEquals(AgentResponseType.DONE, response.getType());
        assertEquals(0, otherCalls.get());
        assertTrue(context.getToolObservations().stream().anyMatch(observation ->
                observation.result().code().equals("TOOL_NOT_REQUESTED")));
    }

    @Test
    void requiresBothSelectedAlgorithmsBeforeAcceptingDone() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new EchoTool());
        registry.register(new CountingTool(new AtomicInteger()));
        Queue<AgentResponse> responses = new ArrayDeque<>(List.of(
                AgentResponse.toolCall("First tool.", new ToolCall(
                        "echo", JsonNodeFactory.instance.objectNode())),
                AgentResponse.done("Stopped early."),
                AgentResponse.toolCall("Second tool.", new ToolCall(
                        "other", JsonNodeFactory.instance.objectNode())),
                AgentResponse.done("Both completed.")));
        AgentContext context = new AgentContext("task-pair", "Use both tools.");
        context.putAttribute("requestedTools", "echo,other");

        AgentResponse response = new AgentLoop(ignored -> responses.remove(), registry, 5).run(context);

        assertEquals(AgentResponseType.DONE, response.getType());
        assertEquals("Both completed.", response.getSummary());
        assertTrue(context.getToolObservations().stream().anyMatch(observation ->
                observation.result().code().equals("INCOMPLETE_REQUESTED_TOOLS")));
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

    private static final class CountingTool implements Tool {
        private final AtomicInteger calls;

        private CountingTool(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override public String name() { return "other"; }
        @Override public String description() { return "Counts calls for testing."; }
        @Override public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
            calls.incrementAndGet();
            return ToolResult.success("Other completed.", input);
        }
    }
}
