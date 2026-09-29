package com.jhz.movielens.agent.tool;

import com.jhz.movielens.agent.core.AgentEvent;
import com.jhz.movielens.agent.core.AgentEventListener;

public final class ToolExecutionContext {
    private final AgentEventListener listener;

    public ToolExecutionContext(AgentEventListener listener) {
        this.listener = listener == null ? AgentEventListener.NOOP : listener;
    }

    public void progress(String stageCode, String message, String toolName, int progress) {
        try {
            listener.onEvent(new AgentEvent(
                    AgentEvent.Type.PROGRESS, stageCode, message, toolName, progress));
        } catch (RuntimeException ignored) {
            // Observability must not be able to fail the tool execution itself.
        }
    }
}
