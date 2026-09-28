package com.jhz.movielens.hadoop.clean;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CleaningGroupDecisionTest {
    @Test
    void keepsOneAndQuarantinesExactDuplicates() {
        CleaningGroupDecision decision = CleaningGroupDecision.decide(List.of("same", "same", "same"));
        assertEquals("same", decision.cleanLine());
        assertEquals(2, decision.quarantinedLines().size());
        assertEquals(2, decision.duplicatesRemoved());
        assertEquals(0, decision.conflictsQuarantined());
    }

    @Test
    void quarantinesEveryRecordWhenBusinessKeyConflicts() {
        CleaningGroupDecision decision = CleaningGroupDecision.decide(List.of("first", "second", "first"));
        assertNull(decision.cleanLine());
        assertEquals(3, decision.quarantinedLines().size());
        assertEquals(0, decision.duplicatesRemoved());
        assertEquals(3, decision.conflictsQuarantined());
    }
}
