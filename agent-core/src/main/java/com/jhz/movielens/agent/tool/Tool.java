package com.jhz.movielens.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.jhz.movielens.agent.protocol.ToolResult;

public interface Tool {
    String name();

    String description();

    ToolResult execute(JsonNode input);
}
