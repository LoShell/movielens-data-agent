package com.jhz.movielens.web.task;

import com.jhz.movielens.agent.core.AgentContext;
import com.jhz.movielens.agent.core.AgentLoop;
import com.jhz.movielens.agent.llm.LlmClient;
import com.jhz.movielens.agent.protocol.AgentResponse;
import com.jhz.movielens.agent.protocol.AgentResponseType;
import com.jhz.movielens.agent.tool.ToolRegistry;
import com.jhz.movielens.web.config.LlmProperties;
import com.jhz.movielens.web.config.JsonTreeConverter;
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
    private final ToolRegistry toolRegistry;
    private final LlmClient llmClient;
    private final PipelineProperties properties;
    private final LlmProperties llmProperties;
    private final JsonTreeConverter jsonTreeConverter;

    public GovernanceTaskService(ToolRegistry toolRegistry, LlmClient llmClient,
                                 PipelineProperties properties, LlmProperties llmProperties,
                                 JsonTreeConverter jsonTreeConverter) {
        this.toolRegistry = toolRegistry;
        this.llmClient = llmClient;
        this.properties = properties;
        this.llmProperties = llmProperties;
        this.jsonTreeConverter = jsonTreeConverter;
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
            AgentContext context = new AgentContext(initial.taskId(), initial.prompt());
            context.putAttribute("taskId", initial.taskId());
            context.putAttribute("registeredInputVersion", initial.inputVersion());
            context.putAttribute("reservedOutputVersion", initial.outputVersion());
            context.putAttribute("registeredRulesVersion", initial.rulesVersion());
            AgentLoop loop = new AgentLoop(
                    llmClient,
                    toolRegistry,
                    llmProperties.getMaxSteps(),
                    task::onAgentEvent);
            AgentResponse response = loop.run(context);
            if (response.getType() != AgentResponseType.DONE) {
                task.failed(response.getSummary() == null ? response.getMessage() : response.getSummary());
                return;
            }
            if (context.getToolObservations().isEmpty()) {
                task.failed("Agent returned DONE without executing a registered tool or producing evidence.");
                return;
            }
            var finalObservation = context.getToolObservations()
                    .get(context.getToolObservations().size() - 1);
            if (!finalObservation.result().success()) {
                task.failed("Agent stopped without a successful tool result: "
                        + finalObservation.result().message());
                return;
            }
            Map<String, Object> result = jsonTreeConverter.toWebMap(finalObservation.result().data());
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
