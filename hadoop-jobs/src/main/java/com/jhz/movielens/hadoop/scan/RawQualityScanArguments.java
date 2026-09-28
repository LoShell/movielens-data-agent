package com.jhz.movielens.hadoop.scan;

import org.apache.hadoop.fs.Path;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record RawQualityScanArguments(
        Path ratings,
        Path users,
        Path movies,
        Path report,
        String taskId,
        String dataVersion,
        String rulesVersion) {

    private static final Set<String> REQUIRED_OPTIONS = Set.of(
            "ratings", "users", "movies", "report", "task-id", "data-version", "rules-version");

    public static RawQualityScanArguments parse(String[] arguments) {
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

        return new RawQualityScanArguments(
                new Path(values.get("ratings")),
                new Path(values.get("users")),
                new Path(values.get("movies")),
                new Path(values.get("report")),
                values.get("task-id"),
                values.get("data-version"),
                values.get("rules-version"));
    }

    public static String usage() {
        return "Usage: RawQualityScanJob "
                + "--ratings <path> --users <path> --movies <path> --report <path> "
                + "--task-id <id> --data-version <version> --rules-version <version>";
    }
}
