package com.jhz.movielens.hadoop.scan;

import java.util.HashSet;
import java.util.Set;

record RecordGroupAnalysis(long duplicateRecords, long conflictingRecords) {
    static RecordGroupAnalysis analyze(Iterable<String> values) {
        long count = 0;
        Set<String> distinct = new HashSet<>();
        for (String value : values) {
            count++;
            distinct.add(value);
        }
        return new RecordGroupAnalysis(count - distinct.size(), distinct.size() > 1 ? distinct.size() : 0);
    }
}
