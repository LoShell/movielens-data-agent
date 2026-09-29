package com.jhz.movielens.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jhz.movielens.agent.protocol.ToolResult;

public interface Tool {
    String name();

    String description();

    default JsonNode inputSchema() {
        return JsonNodeFactory.instance.objectNode()
                .put("type", "object")
                .put("additionalProperties", false);
    }

    ToolResult execute(JsonNode input);

    default ToolResult execute(JsonNode input, ToolExecutionContext context) {
        return execute(input);
    }
}
