package com.jhz.movielens.hadoop.clean;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

final class CleaningReportWriter {
    private static final String[] ACTIONS = {
            "cleanWritten", "invalidQuarantined", "duplicatesRemoved", "conflictsQuarantined"
    };

    private CleaningReportWriter() {
    }

    static void write(Configuration configuration, CleaningArguments arguments,
                      Map<String, Job> jobs, Instant startedAt, Instant finishedAt) throws IOException {
        Path reportPath = arguments.report();
        FileSystem fileSystem = reportPath.getFileSystem(configuration);
        if (reportPath.getParent() != null) {
            fileSystem.mkdirs(reportPath.getParent());
        }
        if (fileSystem.exists(reportPath)) {
            throw new IOException("Report path already exists: " + reportPath);
        }
        try (FSDataOutputStream output = fileSystem.create(reportPath, false);
             Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(buildJson(arguments, jobs, startedAt, finishedAt));
        }
    }

    private static String buildJson(CleaningArguments arguments, Map<String, Job> jobs,
                                    Instant startedAt, Instant finishedAt) throws IOException {
        StringBuilder json = new StringBuilder(2048);
        json.append("{\n");
        field(json, "schemaVersion", "1.0", true, 2);
        field(json, "taskId", arguments.taskId(), true, 2);
        field(json, "inputVersion", arguments.inputVersion(), true, 2);
        field(json, "outputVersion", arguments.outputVersion(), true, 2);
        field(json, "rulesVersion", arguments.rulesVersion(), true, 2);
        field(json, "status", "SUCCEEDED", true, 2);
        field(json, "startedAt", startedAt.toString(), true, 2);
        field(json, "finishedAt", finishedAt.toString(), true, 2);
        field(json, "outputRoot", arguments.outputRoot().toString(), true, 2);

        json.append("  \"expectedTimestampRange\": {\n");
        numberField(json, "minInclusive", arguments.minTimestamp(), true, 4);
        numberField(json, "maxInclusive", arguments.maxTimestamp(), false, 4);
        json.append("  },\n");

        json.append("  \"jobs\": {\n");
        int jobIndex = 0;
        for (Map.Entry<String, Job> entry : jobs.entrySet()) {
            field(json, entry.getKey(), entry.getValue().getJobID().toString(),
                    jobIndex++ < jobs.size() - 1, 4);
        }
        json.append("  },\n");

        json.append("  \"actions\": {\n");
        int datasetIndex = 0;
        for (Map.Entry<String, Job> entry : jobs.entrySet()) {
            String dataset = entry.getKey();
            Counters counters = entry.getValue().getCounters();
            json.append("    \"").append(dataset).append("\": {\n");
            for (int actionIndex = 0; actionIndex < ACTIONS.length; actionIndex++) {
                String action = ACTIONS[actionIndex];
                long value = counters.findCounter(CleaningCounterNames.GROUP,
                        CleaningCounterNames.action(dataset, action)).getValue();
                numberField(json, action, value, actionIndex < ACTIONS.length - 1, 6);
            }
            json.append("    }").append(datasetIndex++ < jobs.size() - 1 ? "," : "").append('\n');
        }
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

    private static void field(StringBuilder json, String name, String value, boolean comma, int indent) {
        json.append(" ".repeat(indent)).append('"').append(escape(name)).append("\": \"")
                .append(escape(value)).append('"').append(comma ? "," : "").append('\n');
    }

    private static void numberField(StringBuilder json, String name, long value, boolean comma, int indent) {
        json.append(" ".repeat(indent)).append('"').append(escape(name)).append("\": ")
                .append(value).append(comma ? "," : "").append('\n');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
