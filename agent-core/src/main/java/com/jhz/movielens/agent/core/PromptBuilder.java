package com.jhz.movielens.agent.core;

import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolRegistry;

import java.util.Map;
import java.util.stream.Collectors;

public final class PromptBuilder {
    public String build(AgentContext context, ToolRegistry toolRegistry) {
        return buildSystemPrompt(context, toolRegistry) + System.lineSeparator()
                + System.lineSeparator() + buildUserPrompt(context);
    }

    public String buildSystemPrompt(AgentContext context, ToolRegistry toolRegistry) {
        return """
                You are the planning and reflection component of a MovieLens data analysis agent.
                The system is extended over multiple iterations. Choose only from the tools currently registered.

                Operating loop:
                1. Understand the user's goal and the registered runtime context.
                2. Plan the next safe action.
                3. Return one TOOL_CALL when a tool is needed.
                4. On the next turn, inspect the real tool observation, reflect on success or failure, and re-plan.
                5. Return DONE only when the request is supported and completed with real evidence.

                Reliability rules:
                - Respond with exactly one JSON object and no Markdown fences.
                - Never invent a tool, version, score, report, model, or execution result.
                - Never claim success before observing a successful tool result.
                - Use only the exact registered identifiers in Runtime context.
                - Reject unrelated, unsafe, or unsupported requests with FAILED without calling a tool.
                - Keep message to a short decision rationale; do not expose private chain-of-thought.
                - A failed tool result must be explained or followed by a safe corrective action.

                Available tools (name, description, JSON input schema):
                %s

                Response format when an action is needed:
                {
                  "type": "TOOL_CALL",
                  "message": "brief reason for this action",
                  "toolCall": {
                    "tool": "registered_tool_name",
                    "input": {}
                  },
                  "summary": null
                }

                Response format when completed:
                {
                  "type": "DONE",
                  "message": "brief completion decision",
                  "toolCall": null,
                  "summary": "grounded result summary"
                }

                Response format when unsupported or impossible:
                {
                  "type": "FAILED",
                  "message": "clear failure reason",
                  "toolCall": null,
                  "summary": "what is missing or unsupported"
                }

                Runtime context:
                %s
                """.formatted(
                formatTools(toolRegistry),
                formatAttributes(context.getAttributes()));
    }

    public String buildUserPrompt(AgentContext context) {
        return """
                User request:
                %s

                Previous actions and observations:
                %s
                """.formatted(context.getUserRequest(), formatHistory(context));
    }

    private String formatTools(ToolRegistry registry) {
        if (registry.all().isEmpty()) {
            return "(no tools registered)";
        }
        return registry.all().stream()
                .map(this::formatTool)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatTool(Tool tool) {
        return "- " + tool.name() + ": " + tool.description()
                + System.lineSeparator() + "  inputSchema=" + tool.inputSchema();
    }

    private String formatAttributes(Map<String, String> attributes) {
        if (attributes.isEmpty()) {
            return "(empty)";
        }
        return attributes.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatHistory(AgentContext context) {
        if (context.getHistory().size() <= 1) {
            return "(empty)";
        }
        return context.getHistory().stream()
                .skip(1)
                .map(message -> message.role() + "[" + message.source() + "]: " + message.content())
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }
}
