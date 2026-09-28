package com.jhz.movielens.web.task;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

final class ManagedTask {
    private final String taskId;
    private final String prompt;
    private final String inputVersion;
    private final String outputVersion;
    private final String rulesVersion;
    private final Instant createdAt = Instant.now();
    private volatile TaskStatus status = TaskStatus.QUEUED;
    private volatile String stage = "等待 Agent 调度";
    private volatile String summary = "";
    private volatile String error = "";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile JsonNode result;

    ManagedTask(String taskId, String prompt, String inputVersion, String outputVersion, String rulesVersion) {
        this.taskId = taskId;
        this.prompt = prompt;
        this.inputVersion = inputVersion;
        this.outputVersion = outputVersion;
        this.rulesVersion = rulesVersion;
    }

    void running() {
        status = TaskStatus.RUNNING;
        stage = "Agent 正在调用 Hadoop 清洗与评分工具";
        startedAt = Instant.now();
    }

    void succeeded(String summary, JsonNode result) {
        this.status = TaskStatus.SUCCEEDED;
        this.stage = "已完成";
        this.summary = summary;
        this.result = result;
        this.finishedAt = Instant.now();
    }

    void failed(String error) {
        this.status = TaskStatus.FAILED;
        this.stage = "执行失败";
        this.error = error;
        this.finishedAt = Instant.now();
    }

    TaskSnapshot snapshot() {
        return new TaskSnapshot(taskId, prompt, inputVersion, outputVersion, rulesVersion,
                status, stage, summary, error, createdAt, startedAt, finishedAt, result);
    }
}
