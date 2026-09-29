package com.jhz.movielens.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jhz.movielens.agent.protocol.ToolResult;
import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {
    @Test
    void exposesEveryRegisteredToolAndRuntimeIdentifier() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FutureIterationTool());
        AgentContext context = new AgentContext("task-1", "分析数据");
        context.putAttribute("registeredInputVersion", "clean-v1");

        String prompt = new PromptBuilder().build(context, registry);

        assertTrue(prompt.contains("future_analysis"));
        assertTrue(prompt.contains("inputSchema"));
        assertTrue(prompt.contains("registeredInputVersion=clean-v1"));
        assertTrue(prompt.contains("分析数据"));
    }

    private static final class FutureIterationTool implements Tool {
        @Override
        public String name() {
            return "future_analysis";
        }

        @Override
        public String description() {
            return "Represents a tool added by a later iteration.";
        }

        @Override
        public JsonNode inputSchema() {
            return JsonNodeFactory.instance.objectNode()
                    .put("type", "object")
                    .set("properties", JsonNodeFactory.instance.objectNode()
                            .set("dataVersion", JsonNodeFactory.instance.objectNode().put("type", "string")));
        }

        @Override
        public ToolResult execute(JsonNode input) {
            return ToolResult.success("ok", input);
        }
    }
}
