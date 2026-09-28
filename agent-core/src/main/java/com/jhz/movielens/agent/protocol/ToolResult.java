package com.jhz.movielens.agent.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public record ToolResult(boolean success, String code, String message, JsonNode data) {
    public ToolResult {
        code = code == null || code.isBlank() ? (success ? "SUCCESS" : "FAILURE") : code;
        message = message == null ? "" : message;
        data = data == null ? JsonNodeFactory.instance.objectNode() : data;
    }

    public static ToolResult success(String message, JsonNode data) {
        return new ToolResult(true, "SUCCESS", message, data);
    }

    public static ToolResult failure(String code, String message) {
        return new ToolResult(false, code, message, JsonNodeFactory.instance.objectNode());
    }
}
