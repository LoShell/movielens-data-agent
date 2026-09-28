package com.jhz.movielens.web.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jhz.movielens.agent.core.AgentContext;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.protocol.AgentResponseType;
import com.jhz.movielens.agent.protocol.ToolResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowPlannerTest {
    @Test
    void callsOnlyTheGovernanceToolThenFinishesFromItsRealResult() {
        WorkflowPlanner planner = new WorkflowPlanner("raw-v1", "clean-v2", "rules-v1");
        AgentContext context = new AgentContext("task-1", "请清洗并评分");

        AgentResponse first = planner.next(context);
        assertEquals(AgentResponseType.TOOL_CALL, first.getType());
        assertEquals(WorkflowPlanner.TOOL_NAME, first.getToolCall().getTool());
        assertEquals("clean-v2", first.getToolCall().getInput().path("outputVersion").asText());

        context.addToolObservation(WorkflowPlanner.TOOL_NAME,
                ToolResult.success("done", JsonNodeFactory.instance.objectNode()));
        assertEquals(AgentResponseType.DONE, planner.next(context).getType());
    }
}
