package com.jhz.movielens.hadoop.score;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DimensionResultTest {
    @Test
    void calculatesPercentageAndClampsAtZero() {
        assertEquals(90.0, DimensionResult.of("test", 10, 100).score(), 0.0001);
        assertEquals(0.0, DimensionResult.of("test", 120, 100).score(), 0.0001);
    }
}
