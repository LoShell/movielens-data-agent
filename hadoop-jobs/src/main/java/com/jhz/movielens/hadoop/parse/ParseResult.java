package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.quality.IssueSeverity;
import com.jhz.movielens.hadoop.quality.QualityIssue;

import java.util.List;
import java.util.Optional;

public record ParseResult<T>(T value, List<QualityIssue> issues) {
    public ParseResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public Optional<T> parsedValue() {
        return Optional.ofNullable(value);
    }

    public boolean isParsed() {
        return value != null;
    }

    public boolean isValid() {
        return value != null && issues.stream().noneMatch(issue -> issue.severity() == IssueSeverity.ERROR);
    }
}
