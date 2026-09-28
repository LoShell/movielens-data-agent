package com.jhz.movielens.hadoop.scan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordGroupAnalysisTest {
    @Test
    void countsExactDuplicatesWithoutConflict() {
        RecordGroupAnalysis analysis = RecordGroupAnalysis.analyze(List.of("A", "A", "A"));
        assertEquals(2, analysis.duplicateRecords());
        assertEquals(0, analysis.conflictingRecords());
    }

    @Test
    void distinguishesDuplicatesFromConflictingVariants() {
        RecordGroupAnalysis analysis = RecordGroupAnalysis.analyze(List.of("A", "A", "B"));
        assertEquals(1, analysis.duplicateRecords());
        assertEquals(2, analysis.conflictingRecords());
    }
}
