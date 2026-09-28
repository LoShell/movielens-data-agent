package com.jhz.movielens.agent.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

public class ToolCall {
    private String tool;
    private JsonNode input = JsonNodeFactory.instance.objectNode();

    public ToolCall() {
    }

    public ToolCall(String tool, JsonNode input) {
        this.tool = tool;
        this.input = input == null ? JsonNodeFactory.instance.objectNode() : input;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public JsonNode getInput() {
        return input;
    }

    public void setInput(JsonNode input) {
        this.input = input == null ? JsonNodeFactory.instance.objectNode() : input;
    }
}
