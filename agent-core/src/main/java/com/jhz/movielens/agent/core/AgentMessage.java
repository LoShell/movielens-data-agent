package com.jhz.movielens.agent.core;

public record AgentMessage(Role role, String source, String content) {
    public AgentMessage {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        source = source == null ? "" : source;
        content = content == null ? "" : content;
    }

    public enum Role {
        USER,
        ASSISTANT,
        TOOL
    }
}
