package com.jhz.movielens.web.config;

import com.jhz.movielens.agent.llm.OpenAiCompatibleLlmClient;
import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class AgentConfiguration {
    @Bean
    ToolRegistry toolRegistry(List<Tool> tools) {
        ToolRegistry registry = new ToolRegistry();
        tools.forEach(registry::register);
        return registry;
    }

    @Bean
    OpenAiCompatibleLlmClient openAiCompatibleLlmClient(
            LlmProperties properties, ToolRegistry toolRegistry) {
        Map<String, Object> extraBody = isDeepSeek(properties) && properties.isDisableThinking()
                ? Map.of("thinking", Map.of("type", "disabled"))
                : Map.of();
        return new OpenAiCompatibleLlmClient(
                properties.getApiKey(),
                properties.getModel(),
                properties.getBaseUrl(),
                properties.getConnectTimeout(),
                properties.getRequestTimeout(),
                properties.getMaxTokens(),
                properties.getTemperature(),
                extraBody,
                toolRegistry);
    }

    private static boolean isDeepSeek(LlmProperties properties) {
        return properties.getProvider() != null
                && properties.getProvider().equalsIgnoreCase("deepseek");
    }
}
