package com.jhz.movielens.agent.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentResponseJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesStructuredToolInput() throws Exception {
        String json = """
                {
                  "type": "TOOL_CALL",
                  "message": "Run the registered Hadoop workflow.",
                  "toolCall": {
                    "tool": "run_data_governance",
                    "input": {
                      "datasetVersion": "raw-v1",
                      "rulesVersion": "rules-v1"
                    }
                  }
                }
                """;

        AgentResponse response = objectMapper.readValue(json, AgentResponse.class);

        assertEquals(AgentResponseType.TOOL_CALL, response.getType());
        assertEquals("run_data_governance", response.getToolCall().getTool());
        assertEquals("raw-v1", response.getToolCall().getInput().path("datasetVersion").asText());
        assertEquals("rules-v1", response.getToolCall().getInput().path("rulesVersion").asText());
    }
}
