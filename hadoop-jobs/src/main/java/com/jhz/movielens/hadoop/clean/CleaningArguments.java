package com.jhz.movielens.hadoop.clean;

import org.apache.hadoop.fs.Path;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record CleaningArguments(Path ratings, Path users, Path movies, Path outputRoot, Path report,
                                String taskId, String inputVersion, String outputVersion,
                                String rulesVersion, long minTimestamp, long maxTimestamp) {
    private static final Set<String> REQUIRED = Set.of(
            "ratings", "users", "movies", "output-root", "report", "task-id", "input-version",
            "output-version", "rules-version", "min-timestamp", "max-timestamp");

    public CleaningArguments {
        if (minTimestamp <= 0 || maxTimestamp <= 0 || minTimestamp > maxTimestamp) {
            throw new IllegalArgumentException("Timestamp range must be positive and ordered.");
        }
    }

    public static CleaningArguments parse(String[] arguments) {
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
            if (!REQUIRED.contains(name)) {
                throw new IllegalArgumentException("Unknown option: " + option);
            }
            String value = arguments[index + 1];
            if (value == null || value.isBlank() || values.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Missing or duplicate value for option: " + option);
            }
        }
        for (String option : REQUIRED) {
            if (!values.containsKey(option)) {
                throw new IllegalArgumentException("Missing required option --" + option + System.lineSeparator() + usage());
            }
        }
        return new CleaningArguments(
                new Path(values.get("ratings")), new Path(values.get("users")), new Path(values.get("movies")),
                new Path(values.get("output-root")), new Path(values.get("report")), values.get("task-id"),
                values.get("input-version"), values.get("output-version"), values.get("rules-version"),
                parseLong(values, "min-timestamp"), parseLong(values, "max-timestamp"));
    }

    private static long parseLong(Map<String, String> values, String option) {
        try {
            return Long.parseLong(values.get(option));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option --" + option + " must be an integer.", exception);
        }
    }

    static String usage() {
        return "Usage: MovieLensCleaningJob --ratings <path> --users <path> --movies <path> "
                + "--output-root <path> --report <path> --task-id <id> --input-version <version> "
                + "--output-version <version> --rules-version <version> "
                + "--min-timestamp <seconds> --max-timestamp <seconds>";
    }
}
