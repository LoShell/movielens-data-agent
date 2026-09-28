package com.jhz.movielens.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.jhz.movielens.agent.protocol.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryTest {
    @Test
    void rejectsDuplicateToolNames() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new StubTool());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(new StubTool()));

        assertEquals("duplicate tool name: stub", exception.getMessage());
    }

    private static final class StubTool implements Tool {
        @Override
        public String name() {
            return "stub";
        }

        @Override
        public String description() {
            return "Test tool.";
        }

        @Override
        public ToolResult execute(JsonNode input) {
            return ToolResult.success("ok", input);
        }
    }
}
