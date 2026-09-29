package com.jhz.movielens.agent.llm;

@FunctionalInterface
public interface TextLlmClient {
    String complete(String systemPrompt, String userPrompt);
}
