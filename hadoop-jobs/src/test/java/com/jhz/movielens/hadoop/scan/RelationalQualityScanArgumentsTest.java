package com.jhz.movielens.hadoop.scan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelationalQualityScanArgumentsTest {
    private static final String[] VALID_ARGUMENTS = {
            "--ratings", "/raw/ratings.dat",
            "--users", "/raw/users.dat",
            "--movies", "/raw/movies.dat",
            "--report", "/reports/relational.json",
            "--task-id", "task-1",
            "--data-version", "raw-v1",
            "--rules-version", "rules-v1",
            "--min-timestamp", "956703932",
            "--max-timestamp", "1046454590"
    };

    @Test
    void parsesTimestampRange() {
        RelationalQualityScanArguments arguments = RelationalQualityScanArguments.parse(VALID_ARGUMENTS);
        assertEquals(956703932L, arguments.minTimestamp());
        assertEquals(1046454590L, arguments.maxTimestamp());
    }

    @Test
    void rejectsReversedTimestampRange() {
        String[] arguments = VALID_ARGUMENTS.clone();
        arguments[15] = "200";
        arguments[17] = "100";
        assertThrows(IllegalArgumentException.class, () -> RelationalQualityScanArguments.parse(arguments));
    }
}
