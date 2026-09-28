package com.jhz.movielens.web.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.jhz.movielens.agent.core.AgentContext;
import com.jhz.movielens.agent.core.AgentLoop;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.protocol.AgentResponseType;
import com.jhz.movielens.agent.tool.ToolRegistry;
import com.jhz.movielens.web.agent.HadoopGovernanceTool;
import com.jhz.movielens.web.agent.WorkflowPlanner;
import com.jhz.movielens.web.config.PipelineProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class GovernanceTaskService {
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final Map<String, ManagedTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HadoopGovernanceTool governanceTool;
    private final PipelineProperties properties;

    public GovernanceTaskService(HadoopGovernanceTool governanceTool, PipelineProperties properties) {
        this.governanceTool = governanceTool;
        this.properties = properties;
    }

    public TaskSnapshot submit(String prompt) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String taskId = "agent-" + ID_TIME.format(java.time.Instant.now()) + "-" + suffix;
        String outputVersion = "clean-" + taskId;
        ManagedTask task = new ManagedTask(taskId, prompt, properties.getInputVersion(),
                outputVersion, properties.getRulesVersion());
        tasks.put(taskId, task);
        executor.submit(() -> execute(task));
        return task.snapshot();
    }

    public TaskSnapshot get(String taskId) {
        ManagedTask task = tasks.get(taskId);
        return task == null ? null : task.snapshot();
    }

    private void execute(ManagedTask task) {
        TaskSnapshot initial = task.snapshot();
        task.running();
        try {
            ToolRegistry registry = new ToolRegistry();
            registry.register(governanceTool);
            AgentContext context = new AgentContext(initial.taskId(), initial.prompt());
            AgentLoop loop = new AgentLoop(
                    new WorkflowPlanner(initial.inputVersion(), initial.outputVersion(), initial.rulesVersion()),
                    registry,
                    3);
            AgentResponse response = loop.run(context);
            if (response.getType() != AgentResponseType.DONE) {
                task.failed(response.getSummary() == null ? response.getMessage() : response.getSummary());
                return;
            }
            JsonNode result = context.getToolObservations().isEmpty()
                    ? null
                    : context.getToolObservations().get(context.getToolObservations().size() - 1).result().data();
            task.succeeded(response.getSummary(), result);
        } catch (RuntimeException exception) {
            task.failed(exception.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
