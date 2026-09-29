package com.jhz.movielens.agent.core;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jhz.movielens.agent.llm.LlmClient;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.protocol.ToolCall;
import com.jhz.movielens.agent.protocol.ToolResult;
import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolRegistry;

public final class AgentLoop {
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;

    public AgentLoop(LlmClient llmClient, ToolRegistry toolRegistry, int maxSteps) {
        if (llmClient == null || toolRegistry == null) {
            throw new IllegalArgumentException("llmClient and toolRegistry must not be null");
        }
        if (maxSteps < 1) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.maxSteps = maxSteps;
    }

    public AgentResponse run(AgentContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        for (int step = 1; step <= maxSteps; step++) {
            AgentResponse response;
            try {
                response = llmClient.next(context);
            } catch (RuntimeException exception) {
                return AgentResponse.failed("LLM call failed: " + exception.getMessage());
            }

            if (response == null || response.getType() == null) {
                return AgentResponse.failed("LLM returned an empty or unsupported response.");
            }

            context.addAssistantMessage(formatAssistantMessage(step, response));

            if (response.isTerminal()) {
                return response;
            }

            if (response.isToolCall()) {
                executeToolCall(context, response.getToolCall());
                continue;
            }

            return AgentResponse.failed("Unsupported response type: " + response.getType());
        }

        return AgentResponse.failed("Agent reached the maximum number of steps: " + maxSteps);
    }

    private void executeToolCall(AgentContext context, ToolCall call) {
        if (call == null || call.getTool() == null || call.getTool().isBlank()) {
            context.addToolObservation("unknown", ToolResult.failure("INVALID_TOOL_CALL", "Missing tool name."));
            return;
        }

        ToolResult result = toolRegistry.find(call.getTool())
                .map(tool -> executeTool(tool, call))
                .orElseGet(() -> ToolResult.failure("UNKNOWN_TOOL", "Unknown tool: " + call.getTool()));
        context.addToolObservation(call.getTool(), result);
    }

    private ToolResult executeTool(Tool tool, ToolCall call) {
        try {
            ToolResult result = tool.execute(call.getInput() == null
                    ? JsonNodeFactory.instance.objectNode()
                    : call.getInput());
            return result == null
                    ? ToolResult.failure("EMPTY_TOOL_RESULT", "Tool returned no result: " + tool.name())
                    : result;
        } catch (RuntimeException exception) {
            return ToolResult.failure("TOOL_EXECUTION_FAILED", exception.getMessage());
        }
    }

    private static String formatAssistantMessage(int step, AgentResponse response) {
        StringBuilder message = new StringBuilder("STEP ").append(step)
                .append(": type=").append(response.getType())
                .append("; message=").append(response.getMessage());
        if (response.getToolCall() != null) {
            message.append("; tool=").append(response.getToolCall().getTool())
                    .append("; input=").append(response.getToolCall().getInput());
        }
        if (response.getSummary() != null) {
            message.append("; summary=").append(response.getSummary());
        }
        return message.toString();
    }
}
