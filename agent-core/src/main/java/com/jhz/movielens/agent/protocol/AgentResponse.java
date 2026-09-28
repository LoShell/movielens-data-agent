package com.jhz.movielens.agent.protocol;

public class AgentResponse {
    private AgentResponseType type;
    private String message;
    private ToolCall toolCall;
    private String summary;

    public AgentResponse() {
    }

    public AgentResponse(AgentResponseType type, String message, ToolCall toolCall, String summary) {
        this.type = type;
        this.message = message;
        this.toolCall = toolCall;
        this.summary = summary;
    }

    public static AgentResponse toolCall(String message, ToolCall toolCall) {
        return new AgentResponse(AgentResponseType.TOOL_CALL, message, toolCall, null);
    }

    public static AgentResponse done(String summary) {
        return new AgentResponse(AgentResponseType.DONE, "Task completed.", null, summary);
    }

    public static AgentResponse failed(String message) {
        return new AgentResponse(AgentResponseType.FAILED, message, null, message);
    }

    public AgentResponseType getType() {
        return type;
    }

    public void setType(AgentResponseType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public void setToolCall(ToolCall toolCall) {
        this.toolCall = toolCall;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public boolean isToolCall() {
        return type == AgentResponseType.TOOL_CALL;
    }

    public boolean isTerminal() {
        return type == AgentResponseType.DONE || type == AgentResponseType.FAILED;
    }
}
