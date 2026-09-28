package com.jhz.movielens.agent.llm;

import com.jhz.movielens.agent.core.AgentContext;
import com.jhz.movielens.agent.protocol.AgentResponse;

@FunctionalInterface
public interface LlmClient {
    AgentResponse next(AgentContext context);
}
