package com.jhz.movielens.web;

import com.jhz.movielens.agent.llm.LlmClient;
import com.jhz.movielens.agent.llm.TextLlmClient;
import com.jhz.movielens.agent.tool.ToolRegistry;
import com.jhz.movielens.web.agent.HadoopGovernanceTool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MovieLensAgentApplicationTest {
    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private TextLlmClient textLlmClient;

    @Test
    void buildsAgentFromDynamicallyDiscoveredTools() {
        assertTrue(toolRegistry.find(HadoopGovernanceTool.TOOL_NAME).isPresent());
        assertNotNull(llmClient);
        assertNotNull(textLlmClient);
    }
}
