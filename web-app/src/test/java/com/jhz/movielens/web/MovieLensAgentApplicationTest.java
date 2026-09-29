package com.jhz.movielens.web;

import com.jhz.movielens.agent.llm.LlmClient;
import com.jhz.movielens.agent.llm.TextLlmClient;
import com.jhz.movielens.agent.tool.ToolRegistry;
import com.jhz.movielens.web.agent.HadoopGovernanceTool;
import com.jhz.movielens.web.task.TaskSnapshot;
import com.jhz.movielens.web.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

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

    @Autowired
    private ObjectMapper webObjectMapper;

    @Test
    void buildsAgentFromDynamicallyDiscoveredTools() {
        assertTrue(toolRegistry.find(HadoopGovernanceTool.TOOL_NAME).isPresent());
        assertNotNull(llmClient);
        assertNotNull(textLlmClient);
    }

    @Test
    void serializesWebSafeTaskResultWithOriginalReportFields() throws Exception {
        Map<String, Object> report = Map.of(
                "quality", Map.of("dimensions", Map.of("Accurate",
                        Map.of("before", Map.of("score", 95.96)))),
                "cleaning", Map.of("actions", Map.of("ratings",
                        Map.of("cleanWritten", 894993))));
        TaskSnapshot snapshot = new TaskSnapshot("task-1", "test", "raw-v1", "clean-v1",
                "quality-rules-v1", TaskStatus.SUCCEEDED, "已完成", "done", "",
                Instant.now(), Instant.now(), Instant.now(), report);

        var serialized = webObjectMapper.readTree(webObjectMapper.writeValueAsString(snapshot));

        assertTrue(Math.abs(serialized.path("result").path("quality").path("dimensions")
                .path("Accurate").path("before").path("score").asDouble() - 95.96) < 0.001);
        assertTrue(serialized.path("result").path("cleaning").path("actions")
                .path("ratings").path("cleanWritten").asInt() == 894993);
    }
}
