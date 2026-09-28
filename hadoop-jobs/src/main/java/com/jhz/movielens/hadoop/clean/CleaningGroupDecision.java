package com.jhz.movielens.hadoop.clean;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record CleaningGroupDecision(String cleanLine, List<String> quarantinedLines,
                             long duplicatesRemoved, long conflictsQuarantined) {
    static CleaningGroupDecision decide(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A cleaning group cannot be empty.");
        }
        Set<String> distinct = new LinkedHashSet<>(lines);
        if (distinct.size() == 1) {
            String clean = distinct.iterator().next();
            return new CleaningGroupDecision(clean, lines.subList(1, lines.size()), lines.size() - 1L, 0);
        }
        return new CleaningGroupDecision(null, List.copyOf(lines), 0, lines.size());
    }
}
