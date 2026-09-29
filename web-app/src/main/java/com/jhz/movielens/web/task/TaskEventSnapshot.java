package com.jhz.movielens.web.task;

import java.time.Instant;

public record TaskEventSnapshot(Instant timestamp, String type, String stageCode,
                                String message, String toolName, int progress) {
}
