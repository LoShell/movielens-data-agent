package com.jhz.movielens.agent.core;

public record AgentEvent(Type type, String stageCode, String message,
                         String toolName, int progress) {
    public AgentEvent {
        if (type == null) {
            throw new IllegalArgumentException("event type must not be null");
        }
        stageCode = stageCode == null ? "" : stageCode;
        message = message == null ? "" : message;
        toolName = toolName == null ? "" : toolName;
        progress = Math.max(0, Math.min(100, progress));
    }

    public enum Type {
        PLAN,
        ACTION,
        PROGRESS,
        OBSERVATION,
        FINAL,
        ERROR
    }
}
