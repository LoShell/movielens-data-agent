package com.jhz.movielens.hadoop.scan;

import org.apache.hadoop.fs.Path;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record RelationalQualityScanArguments(
        Path ratings,
        Path users,
        Path movies,
        Path report,
        String taskId,
        String dataVersion,
        String rulesVersion,
        long minTimestamp,
        long maxTimestamp) {

    private static final Set<String> REQUIRED_OPTIONS = Set.of(
            "ratings", "users", "movies", "report", "task-id", "data-version", "rules-version",
            "min-timestamp", "max-timestamp");

    public RelationalQualityScanArguments {
        if (minTimestamp <= 0 || maxTimestamp <= 0 || minTimestamp > maxTimestamp) {
            throw new IllegalArgumentException("Timestamp range must be positive and min-timestamp <= max-timestamp.");
        }
    }

    public static RelationalQualityScanArguments parse(String[] arguments) {
        if (arguments == null || arguments.length == 0 || arguments.length % 2 != 0) {
            throw new IllegalArgumentException(usage());
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < arguments.length; index += 2) {
            String option = arguments[index];
            if (option == null || !option.startsWith("--")) {
                throw new IllegalArgumentException("Expected an option starting with --: " + option);
            }
            String name = option.substring(2);
            if (!REQUIRED_OPTIONS.contains(name)) {
                throw new IllegalArgumentException("Unknown option: " + option);
            }
            String value = arguments[index + 1];
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing value for option: " + option);
            }
            if (values.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate option: " + option);
            }
        }

        for (String option : REQUIRED_OPTIONS) {
            if (!values.containsKey(option)) {
                throw new IllegalArgumentException("Missing required option --" + option + System.lineSeparator() + usage());
            }
        }

        return new RelationalQualityScanArguments(
                new Path(values.get("ratings")),
                new Path(values.get("users")),
                new Path(values.get("movies")),
                new Path(values.get("report")),
                values.get("task-id"),
                values.get("data-version"),
                values.get("rules-version"),
                parseTimestamp(values, "min-timestamp"),
                parseTimestamp(values, "max-timestamp"));
    }

    private static long parseTimestamp(Map<String, String> values, String name) {
        try {
            return Long.parseLong(values.get(name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option --" + name + " must be an integer Unix timestamp.", exception);
        }
    }

    public static String usage() {
        return "Usage: RelationalQualityScanJob "
                + "--ratings <path> --users <path> --movies <path> --report <path> "
                + "--task-id <id> --data-version <version> --rules-version <version> "
                + "--min-timestamp <seconds> --max-timestamp <seconds>";
    }
}
