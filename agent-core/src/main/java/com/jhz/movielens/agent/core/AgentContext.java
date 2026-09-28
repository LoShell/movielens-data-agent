package com.jhz.movielens.agent.core;

import com.jhz.movielens.agent.protocol.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AgentContext {
    private final String taskId;
    private final String userRequest;
    private final List<AgentMessage> history = new ArrayList<>();

    public AgentContext(String userRequest) {
        this(UUID.randomUUID().toString(), userRequest);
    }

    public AgentContext(String taskId, String userRequest) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("userRequest must not be blank");
        }
        this.taskId = taskId;
        this.userRequest = userRequest;
        addUserMessage(userRequest);
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUserRequest() {
        return userRequest;
    }

    public void addUserMessage(String message) {
        history.add(new AgentMessage(AgentMessage.Role.USER, "user", message));
    }

    public void addAssistantMessage(String message) {
        history.add(new AgentMessage(AgentMessage.Role.ASSISTANT, "agent", message));
    }

    public void addToolObservation(String toolName, ToolResult result) {
        String content = "code=%s; success=%s; message=%s; data=%s".formatted(
                result.code(), result.success(), result.message(), result.data());
        history.add(new AgentMessage(AgentMessage.Role.TOOL, toolName, content));
    }

    public List<AgentMessage> getHistory() {
        return List.copyOf(history);
    }
}
