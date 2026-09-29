package com.jhz.movielens.web.task;

import java.time.Instant;
import java.util.Map;

public record TaskSnapshot(String taskId, String prompt, String inputVersion, String outputVersion,
                           String rulesVersion, TaskStatus status, String stage, String summary,
                           String error, Instant createdAt, Instant startedAt, Instant finishedAt,
                           Map<String, Object> result) {
}
