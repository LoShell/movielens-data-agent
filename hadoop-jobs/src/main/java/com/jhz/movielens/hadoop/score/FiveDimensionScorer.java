package com.jhz.movielens.hadoop.score;

import java.util.LinkedHashMap;
import java.util.Map;

final class FiveDimensionScorer {
    private FiveDimensionScorer() {
    }

    static Map<String, DimensionResult> score(QualitySnapshot snapshot) {
        Map<String, DimensionResult> results = new LinkedHashMap<>();
        long total = snapshot.totalRecords();

        long accurateDefects = snapshot.basicIssues(
                "INVALID_INTEGER", "INVALID_LONG", "NON_POSITIVE_IDENTIFIER", "RATING_OUT_OF_RANGE",
                "NON_POSITIVE_TIMESTAMP", "INVALID_GENDER", "INVALID_AGE_CATEGORY",
                "INVALID_OCCUPATION_CATEGORY", "UNKNOWN_GENRE");
        results.put("Accurate", DimensionResult.of("Accurate", accurateDefects, total));

        long completeDefects = snapshot.basicIssues("EMPTY_LINE", "REQUIRED_FIELD_MISSING");
        results.put("Complete", DimensionResult.of("Complete", completeDefects, total));

        long uniqueDefects = snapshot.relationalIssues("DUPLICATE_RECORD", "CONFLICTING_RECORD");
        results.put("Unique", DimensionResult.of(
                "Unique", uniqueDefects, snapshot.relationallyCheckedRecords()));

        long timelyDefects = snapshot.basicIssues("NON_POSITIVE_TIMESTAMP")
                + snapshot.relationalIssues("TIMESTAMP_BEFORE_EXPECTED_RANGE", "TIMESTAMP_AFTER_EXPECTED_RANGE");
        results.put("Up-to-date", DimensionResult.of("Up-to-date", timelyDefects, snapshot.ratingRecords()));

        long consistentDefects = snapshot.basicIssues(
                "FIELD_COUNT_MISMATCH", "INVALID_INTEGER", "INVALID_LONG", "INVALID_GENDER",
                "INVALID_AGE_CATEGORY", "INVALID_OCCUPATION_CATEGORY", "DUPLICATE_GENRE", "UNKNOWN_GENRE")
                + snapshot.relationalIssues(
                "MISSING_USER_REFERENCE", "MISSING_MOVIE_REFERENCE", "CONFLICTING_RECORD");
        results.put("Consistent", DimensionResult.of("Consistent", consistentDefects, total));
        return results;
    }
}
