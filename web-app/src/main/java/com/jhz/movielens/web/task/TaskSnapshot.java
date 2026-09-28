package com.jhz.movielens.web.task;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record TaskSnapshot(String taskId, String prompt, String inputVersion, String outputVersion,
                           String rulesVersion, TaskStatus status, String stage, String summary,
                           String error, Instant createdAt, Instant startedAt, Instant finishedAt,
                           JsonNode result) {
}
