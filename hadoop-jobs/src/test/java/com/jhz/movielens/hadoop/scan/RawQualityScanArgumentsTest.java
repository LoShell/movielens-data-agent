package com.jhz.movielens.hadoop.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawQualityScanArgumentsTest {
    @Test
    void parsesRequiredVersionedArguments() {
        RawQualityScanArguments arguments = RawQualityScanArguments.parse(new String[]{
                "--ratings", "/movielens/raw/v1/ratings.dat",
                "--users", "/movielens/raw/v1/users.dat",
                "--movies", "/movielens/raw/v1/movies.dat",
                "--report", "/movielens/reports/task-1/raw-quality.json",
                "--task-id", "task-1",
                "--data-version", "raw-v1",
                "--rules-version", "quality-rules-v1"
        });

        assertEquals("task-1", arguments.taskId());
        assertEquals("raw-v1", arguments.dataVersion());
        assertEquals("quality-rules-v1", arguments.rulesVersion());
        assertEquals("/movielens/raw/v1/ratings.dat", arguments.ratings().toString());
    }

    @Test
    void rejectsMissingVersionInformation() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RawQualityScanArguments.parse(new String[]{
                        "--ratings", "ratings.dat",
                        "--users", "users.dat",
                        "--movies", "movies.dat",
                        "--report", "report.json",
                        "--task-id", "task-1"
                }));

        assertTrue(exception.getMessage().contains("Missing required option"));
    }

    @Test
    void rejectsUnknownOptions() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RawQualityScanArguments.parse(new String[]{"--unknown", "value"}));

        assertEquals("Unknown option: --unknown", exception.getMessage());
    }
}
