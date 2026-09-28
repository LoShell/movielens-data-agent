package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.quality.IssueCode;
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

final class RawQualityReportWriter {
    private static final String[] DATASETS = {"ratings", "users", "movies"};
    private static final String[] RECORD_METRICS = {"total", "parsed", "valid", "invalid"};

    private RawQualityReportWriter() {
    }

    static void write(
            Configuration configuration,
            RawQualityScanArguments arguments,
            Job job,
            Instant startedAt,
            Instant finishedAt) throws IOException {
        Path reportPath = arguments.report();
        FileSystem fileSystem = reportPath.getFileSystem(configuration);
        Path parent = reportPath.getParent();
        if (parent != null) {
            fileSystem.mkdirs(parent);
        }
        if (fileSystem.exists(reportPath)) {
            throw new IOException("Report path already exists: " + reportPath);
        }

        String json = buildJson(arguments, job, startedAt, finishedAt);
        try (FSDataOutputStream output = fileSystem.create(reportPath, false);
             Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(json);
        }
    }

    private static String buildJson(
            RawQualityScanArguments arguments,
            Job job,
            Instant startedAt,
            Instant finishedAt) throws IOException {
        Counters counters = job.getCounters();
        StringBuilder json = new StringBuilder(2048);
        json.append("{\n");
        field(json, "schemaVersion", "1.0", true, 2);
        field(json, "taskId", arguments.taskId(), true, 2);
        field(json, "dataVersion", arguments.dataVersion(), true, 2);
        field(json, "rulesVersion", arguments.rulesVersion(), true, 2);
        field(json, "hadoopJobId", job.getJobID().toString(), true, 2);
        field(json, "status", "SUCCEEDED", true, 2);
        field(json, "startedAt", startedAt.toString(), true, 2);
        field(json, "finishedAt", finishedAt.toString(), true, 2);

        json.append("  \"inputs\": {\n");
        field(json, "ratings", arguments.ratings().toString(), true, 4);
        field(json, "users", arguments.users().toString(), true, 4);
        field(json, "movies", arguments.movies().toString(), false, 4);
        json.append("  },\n");

        json.append("  \"records\": {\n");
        for (int datasetIndex = 0; datasetIndex < DATASETS.length; datasetIndex++) {
            String dataset = DATASETS[datasetIndex];
            json.append("    \"").append(dataset).append("\": {\n");
            for (int metricIndex = 0; metricIndex < RECORD_METRICS.length; metricIndex++) {
                String metric = RECORD_METRICS[metricIndex];
                long value = counters.findCounter(
                        QualityCounterNames.RECORD_GROUP,
                        QualityCounterNames.record(dataset, metric)).getValue();
                numberField(json, metric, value, metricIndex < RECORD_METRICS.length - 1, 6);
            }
            json.append("    }").append(datasetIndex < DATASETS.length - 1 ? "," : "").append("\n");
        }
        json.append("  },\n");

        json.append("  \"issues\": {\n");
        for (int datasetIndex = 0; datasetIndex < DATASETS.length; datasetIndex++) {
            String dataset = DATASETS[datasetIndex];
            json.append("    \"").append(dataset).append("\": {\n");
            IssueCode[] issueCodes = IssueCode.values();
            for (int issueIndex = 0; issueIndex < issueCodes.length; issueIndex++) {
                String issueCode = issueCodes[issueIndex].name();
                long value = counters.findCounter(
                        QualityCounterNames.ISSUE_GROUP,
                        QualityCounterNames.issue(dataset, issueCode)).getValue();
                numberField(json, issueCode, value, issueIndex < issueCodes.length - 1, 6);
            }
            json.append("    }").append(datasetIndex < DATASETS.length - 1 ? "," : "").append("\n");
        }
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
    }

    private static void field(StringBuilder json, String name, String value, boolean comma, int indent) {
        json.append(" ".repeat(indent))
                .append('"').append(escape(name)).append("\": \"")
                .append(escape(value)).append('"');
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void numberField(StringBuilder json, String name, long value, boolean comma, int indent) {
        json.append(" ".repeat(indent))
                .append('"').append(escape(name)).append("\": ")
                .append(value);
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
