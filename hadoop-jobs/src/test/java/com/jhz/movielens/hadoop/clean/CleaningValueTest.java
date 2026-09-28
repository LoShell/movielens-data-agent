package com.jhz.movielens.hadoop.clean;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CleaningValueTest {
    @Test
    void roundTripsOriginalLine() {
        CleaningValue original = CleaningValue.invalid("FIELD_COUNT_MISMATCH", "1::bad title::Drama");
        assertEquals(original, CleaningValue.decode(original.encode()));
    }
}
