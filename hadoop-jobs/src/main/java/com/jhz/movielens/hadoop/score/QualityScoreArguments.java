package com.jhz.movielens.hadoop.score;

import org.apache.hadoop.fs.Path;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record QualityScoreArguments(Path rawBasic, Path rawRelational, Path cleanBasic,
                                    Path cleanRelational, Path report, String taskId,
                                    String inputVersion, String outputVersion, String rulesVersion,
                                    long t1, long t2) {
    private static final Set<String> REQUIRED = Set.of(
            "raw-basic", "raw-relational", "clean-basic", "clean-relational", "report", "task-id",
            "input-version", "output-version", "rules-version", "t1", "t2");

    public QualityScoreArguments {
        if (t1 <= 0 || t2 <= 0 || t1 >= t2) {
            throw new IllegalArgumentException("T1 and T2 must be positive and T1 < T2.");
        }
    }

    public static QualityScoreArguments parse(String[] arguments) {
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
            String value = arguments[index + 1];
            if (!REQUIRED.contains(name) || value == null || value.isBlank()
                    || values.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Unknown, missing, or duplicate option: " + option);
            }
        }
        for (String name : REQUIRED) {
            if (!values.containsKey(name)) {
                throw new IllegalArgumentException("Missing required option --" + name);
            }
        }
        return new QualityScoreArguments(
                new Path(values.get("raw-basic")), new Path(values.get("raw-relational")),
                new Path(values.get("clean-basic")), new Path(values.get("clean-relational")),
                new Path(values.get("report")), values.get("task-id"), values.get("input-version"),
                values.get("output-version"), values.get("rules-version"),
                parseLong(values, "t1"), parseLong(values, "t2"));
    }

    private static long parseLong(Map<String, String> values, String name) {
        try {
            return Long.parseLong(values.get(name));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option --" + name + " must be an integer Unix timestamp.", exception);
        }
    }

    private static String usage() {
        return "Usage: QualityScoreTool --raw-basic <path> --raw-relational <path> "
                + "--clean-basic <path> --clean-relational <path> --report <path> --task-id <id> "
                + "--input-version <version> --output-version <version> --rules-version <version> "
                + "--t1 <seconds> --t2 <seconds>";
    }
}
