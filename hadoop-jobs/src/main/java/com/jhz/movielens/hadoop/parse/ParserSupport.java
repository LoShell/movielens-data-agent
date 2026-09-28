package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.quality.IssueCode;
import com.jhz.movielens.hadoop.quality.QualityIssue;

import java.util.List;

final class ParserSupport {
    private ParserSupport() {
    }

    static String[] split(String line, int expectedFields, List<QualityIssue> issues) {
        if (line == null || line.isBlank()) {
            issues.add(QualityIssue.error(IssueCode.EMPTY_LINE, "record", "Record is empty."));
            return null;
        }

        String[] fields = line.split("::", -1);
        if (fields.length != expectedFields) {
            issues.add(QualityIssue.error(
                    IssueCode.FIELD_COUNT_MISMATCH,
                    "record",
                    "Expected %d fields but found %d.".formatted(expectedFields, fields.length)));
            return null;
        }
        return fields;
    }

    static String requiredText(String rawValue, String field, List<QualityIssue> issues) {
        if (rawValue == null || rawValue.isBlank()) {
            issues.add(QualityIssue.error(
                    IssueCode.REQUIRED_FIELD_MISSING, field, "Required field is missing."));
            return null;
        }
        return rawValue.trim();
    }

    static Integer integer(String rawValue, String field, List<QualityIssue> issues) {
        String value = requiredText(rawValue, field, issues);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            issues.add(QualityIssue.error(
                    IssueCode.INVALID_INTEGER, field, "Value is not an integer: " + value));
            return null;
        }
    }

    static Long longValue(String rawValue, String field, List<QualityIssue> issues) {
        String value = requiredText(rawValue, field, issues);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            issues.add(QualityIssue.error(
                    IssueCode.INVALID_LONG, field, "Value is not a long integer: " + value));
            return null;
        }
    }

    static void requirePositiveIdentifier(int value, String field, List<QualityIssue> issues) {
        if (value <= 0) {
            issues.add(QualityIssue.error(
                    IssueCode.NON_POSITIVE_IDENTIFIER, field, "Identifier must be positive."));
        }
    }
}
