package com.jhz.movielens.hadoop.quality;

public record QualityIssue(IssueCode code, IssueSeverity severity, String field, String message) {
    public QualityIssue {
        if (code == null || severity == null) {
            throw new IllegalArgumentException("code and severity must not be null");
        }
        field = field == null ? "record" : field;
        message = message == null ? "" : message;
    }

    public static QualityIssue error(IssueCode code, String field, String message) {
        return new QualityIssue(code, IssueSeverity.ERROR, field, message);
    }

    public static QualityIssue warning(IssueCode code, String field, String message) {
        return new QualityIssue(code, IssueSeverity.WARNING, field, message);
    }
}
