package com.jhz.movielens.web.task;

import com.jhz.movielens.agent.core.AgentEvent;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class ManagedTask {
    private static final int MAX_EVENTS = 80;
    private static final List<StepDefinition> WORKFLOW_STEPS = List.of(
            new StepDefinition("AGENT_PLANNING", "理解请求"),
            new StepDefinition("RAW_QUALITY_SCAN", "质量扫描"),
            new StepDefinition("RELATIONAL_CHECK", "跨表检查"),
            new StepDefinition("CLEANING", "数据清洗"),
            new StepDefinition("CLEAN_VALIDATION", "复检验证"),
            new StepDefinition("QUALITY_SCORING", "五维评分"),
            new StepDefinition("AGENT_SUMMARIZING", "结果解释"),
            new StepDefinition("COMPLETED", "完成"));
    private final String taskId;
    private final String prompt;
    private final String inputVersion;
    private final String outputVersion;
    private final String rulesVersion;
    private final Instant createdAt = Instant.now();
    private volatile TaskStatus status = TaskStatus.QUEUED;
    private volatile String stageCode = "QUEUED";
    private volatile String stage = "等待 Agent 调度";
    private volatile String selectedTool = "";
    private volatile int progress;
    private volatile String summary = "";
    private volatile String error = "";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile Instant updatedAt = createdAt;
    private volatile Map<String, Object> result;
    private final List<TaskEventSnapshot> events = new ArrayList<>();

    ManagedTask(String taskId, String prompt, String inputVersion, String outputVersion, String rulesVersion) {
        this.taskId = taskId;
        this.prompt = prompt;
        this.inputVersion = inputVersion;
        this.outputVersion = outputVersion;
        this.rulesVersion = rulesVersion;
    }

    synchronized void running() {
        status = TaskStatus.RUNNING;
        stageCode = "AGENT_PLANNING";
        stage = "Agent 正在理解请求并规划工具调用";
        progress = 2;
        startedAt = Instant.now();
        updatedAt = startedAt;
        addEvent(new TaskEventSnapshot(updatedAt, "PLAN", stageCode, stage, "", progress));
    }

    synchronized void onAgentEvent(AgentEvent event) {
        if (event == null || status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED) {
            return;
        }
        if (!event.toolName().isBlank()) {
            selectedTool = event.toolName();
        }
        stageCode = event.stageCode();
        stage = event.message();
        progress = Math.max(progress, event.progress());
        updatedAt = Instant.now();
        TaskEventSnapshot snapshot = new TaskEventSnapshot(updatedAt, event.type().name(),
                event.stageCode(), event.message(), event.toolName(), progress);
        if (event.type() == AgentEvent.Type.PROGRESS && !events.isEmpty()) {
            TaskEventSnapshot last = events.get(events.size() - 1);
            if (last.type().equals("PROGRESS") && last.stageCode().equals(snapshot.stageCode())) {
                events.set(events.size() - 1, snapshot);
                return;
            }
        }
        addEvent(snapshot);
    }

    synchronized void succeeded(String summary, Map<String, Object> result) {
        this.status = TaskStatus.SUCCEEDED;
        this.stageCode = "COMPLETED";
        this.stage = "已完成";
        this.progress = 100;
        this.summary = summary;
        this.result = result;
        this.finishedAt = Instant.now();
        this.updatedAt = finishedAt;
    }

    synchronized void failed(String error) {
        this.status = TaskStatus.FAILED;
        this.stageCode = "FAILED";
        this.stage = "执行失败";
        this.error = error;
        this.finishedAt = Instant.now();
        this.updatedAt = finishedAt;
        addEvent(new TaskEventSnapshot(updatedAt, "ERROR", stageCode, error, selectedTool, progress));
    }

    synchronized TaskSnapshot snapshot() {
        return new TaskSnapshot(taskId, prompt, inputVersion, outputVersion, rulesVersion,
                status, stageCode, stage, selectedTool, progress, updatedAt,
                buildSteps(), List.copyOf(events), summary, error,
                createdAt, startedAt, finishedAt, result);
    }

    private void addEvent(TaskEventSnapshot event) {
        if (events.size() == MAX_EVENTS) {
            events.remove(0);
        }
        events.add(event);
    }

    private List<TaskStepSnapshot> buildSteps() {
        int current = currentStepIndex();
        List<TaskStepSnapshot> resultSteps = new ArrayList<>(WORKFLOW_STEPS.size());
        for (int index = 0; index < WORKFLOW_STEPS.size(); index++) {
            StepDefinition definition = WORKFLOW_STEPS.get(index);
            TaskStepSnapshot.Status stepStatus;
            if (status == TaskStatus.SUCCEEDED) {
                stepStatus = TaskStepSnapshot.Status.DONE;
            } else if (status == TaskStatus.FAILED && index == current) {
                stepStatus = TaskStepSnapshot.Status.FAILED;
            } else if (index < current) {
                stepStatus = TaskStepSnapshot.Status.DONE;
            } else if (index == current && status != TaskStatus.QUEUED) {
                stepStatus = TaskStepSnapshot.Status.RUNNING;
            } else {
                stepStatus = TaskStepSnapshot.Status.PENDING;
            }
            resultSteps.add(new TaskStepSnapshot(definition.code(), definition.label(), stepStatus));
        }
        return resultSteps;
    }

    private int currentStepIndex() {
        return switch (stageCode) {
            case "RAW_QUALITY_SCAN" -> 1;
            case "RELATIONAL_CHECK" -> 2;
            case "CLEANING" -> 3;
            case "CLEAN_VALIDATION" -> 4;
            case "QUALITY_SCORING" -> 5;
            case "TOOL_FAILED" -> Math.max(0, completedStageFromProgress());
            case "TOOL_COMPLETED", "AGENT_SUMMARIZING" -> 6;
            case "COMPLETED" -> 7;
            case "FAILED" -> Math.max(0, completedStageFromProgress());
            default -> 0;
        };
    }

    private int completedStageFromProgress() {
        if (progress >= 95) return 6;
        if (progress >= 82) return 5;
        if (progress >= 68) return 4;
        if (progress >= 40) return 3;
        if (progress >= 25) return 2;
        if (progress >= 10) return 1;
        return 0;
    }

    private record StepDefinition(String code, String label) {
    }
}
