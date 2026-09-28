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

final class RelationalQualityReportWriter {
    private static final String[] DATASETS = {"ratings", "users", "movies"};
    private static final IssueCode[] RELATIONAL_ISSUES = {
            IssueCode.MISSING_USER_REFERENCE,
            IssueCode.MISSING_MOVIE_REFERENCE,
            IssueCode.TIMESTAMP_BEFORE_EXPECTED_RANGE,
            IssueCode.TIMESTAMP_AFTER_EXPECTED_RANGE,
            IssueCode.DUPLICATE_RECORD,
            IssueCode.CONFLICTING_RECORD
    };

    private RelationalQualityReportWriter() {
    }

    static void write(Configuration configuration, RelationalQualityScanArguments arguments,
                      Job job, Instant startedAt, Instant finishedAt) throws IOException {
        Path reportPath = arguments.report();
        FileSystem fileSystem = reportPath.getFileSystem(configuration);
        if (reportPath.getParent() != null) {
            fileSystem.mkdirs(reportPath.getParent());
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

    private static String buildJson(RelationalQualityScanArguments arguments, Job job,
                                    Instant startedAt, Instant finishedAt) throws IOException {
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

        json.append("  \"expectedTimestampRange\": {\n");
        numberField(json, "minInclusive", arguments.minTimestamp(), true, 4);
        numberField(json, "maxInclusive", arguments.maxTimestamp(), false, 4);
        json.append("  },\n");

        json.append("  \"records\": {\n");
        for (int index = 0; index < DATASETS.length; index++) {
            String dataset = DATASETS[index];
            long checked = counters.findCounter(QualityCounterNames.RECORD_GROUP,
                    QualityCounterNames.record(dataset, "relationallyChecked")).getValue();
            json.append("    \"").append(dataset).append("\": {\n");
            numberField(json, "relationallyChecked", checked, false, 6);
            json.append("    }").append(index < DATASETS.length - 1 ? "," : "").append('\n');
        }
        json.append("  },\n");

        json.append("  \"issues\": {\n");
        for (int datasetIndex = 0; datasetIndex < DATASETS.length; datasetIndex++) {
            String dataset = DATASETS[datasetIndex];
            json.append("    \"").append(dataset).append("\": {\n");
            for (int issueIndex = 0; issueIndex < RELATIONAL_ISSUES.length; issueIndex++) {
                String issue = RELATIONAL_ISSUES[issueIndex].name();
                long value = counters.findCounter(QualityCounterNames.ISSUE_GROUP,
                        QualityCounterNames.issue(dataset, issue)).getValue();
                numberField(json, issue, value, issueIndex < RELATIONAL_ISSUES.length - 1, 6);
            }
            json.append("    }").append(datasetIndex < DATASETS.length - 1 ? "," : "").append('\n');
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
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
