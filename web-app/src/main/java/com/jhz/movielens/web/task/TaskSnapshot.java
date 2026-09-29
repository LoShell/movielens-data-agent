package com.jhz.movielens.web.task;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TaskSnapshot(String taskId, String prompt, String inputVersion, String outputVersion,
                           String rulesVersion, TaskStatus status, String stageCode, String stage,
                           String selectedTool, int progress, Instant updatedAt,
                           List<TaskStepSnapshot> steps, List<TaskEventSnapshot> events, String summary,
                           String error, Instant createdAt, Instant startedAt, Instant finishedAt,
                           Map<String, Object> result) {
}
