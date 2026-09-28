package com.jhz.movielens.web.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jhz.movielens.agent.core.AgentContext;
import com.jhz.movielens.agent.core.ToolObservation;
import com.jhz.movielens.agent.llm.LlmClient;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.protocol.ToolCall;

public final class WorkflowPlanner implements LlmClient {
    public static final String TOOL_NAME = "run_movielens_governance";
    private final String inputVersion;
    private final String outputVersion;
    private final String rulesVersion;

    public WorkflowPlanner(String inputVersion, String outputVersion, String rulesVersion) {
        this.inputVersion = inputVersion;
        this.outputVersion = outputVersion;
        this.rulesVersion = rulesVersion;
    }

    @Override
    public AgentResponse next(AgentContext context) {
        if (context.getToolObservations().isEmpty()) {
            ObjectNode input = JsonNodeFactory.instance.objectNode();
            input.put("taskId", context.getTaskId());
            input.put("inputVersion", inputVersion);
            input.put("outputVersion", outputVersion);
            input.put("rulesVersion", rulesVersion);
            input.put("request", context.getUserRequest());
            return AgentResponse.toolCall(
                    "调用 Hadoop 执行清洗、隔离、复检和五维评分。",
                    new ToolCall(TOOL_NAME, input));
        }

        ToolObservation observation = context.getToolObservations()
                .get(context.getToolObservations().size() - 1);
        if (!observation.result().success()) {
            return AgentResponse.failed("Hadoop 工具执行失败：" + observation.result().message());
        }
        return AgentResponse.done("MovieLens 数据治理任务已完成，结果来自实际 Hadoop 作业与 HDFS 报告。");
    }
}
