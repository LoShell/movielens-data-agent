package com.jhz.movielens.agent.core;

@FunctionalInterface
public interface AgentEventListener {
    AgentEventListener NOOP = event -> {
    };

    void onEvent(AgentEvent event);
}
