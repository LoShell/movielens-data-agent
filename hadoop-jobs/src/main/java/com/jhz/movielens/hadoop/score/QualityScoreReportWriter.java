package com.jhz.movielens.hadoop.score;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

final class QualityScoreReportWriter {
    private QualityScoreReportWriter() {
    }

    static void write(Configuration configuration, QualityScoreArguments arguments,
                      Map<String, DimensionResult> before, Map<String, DimensionResult> after) throws IOException {
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
            writer.write(buildJson(arguments, before, after));
        }
    }

    private static String buildJson(QualityScoreArguments arguments,
                                    Map<String, DimensionResult> before,
                                    Map<String, DimensionResult> after) {
        StringBuilder json = new StringBuilder(4096);
        json.append("{\n");
        field(json, "schemaVersion", "1.0", true, 2);
        field(json, "taskId", arguments.taskId(), true, 2);
        field(json, "inputVersion", arguments.inputVersion(), true, 2);
        field(json, "outputVersion", arguments.outputVersion(), true, 2);
        field(json, "rulesVersion", arguments.rulesVersion(), true, 2);
        field(json, "status", "SUCCEEDED", true, 2);
        field(json, "generatedAt", Instant.now().toString(), true, 2);

        json.append("  \"timeBoundaries\": {\n");
        boundary(json, "T1", arguments.t1(), "training: timestamp <= T1", true);
        boundary(json, "T2", arguments.t2(), "validation: T1 < timestamp <= T2; test: timestamp > T2", false);
        json.append("  },\n");

        json.append("  \"dimensions\": {\n");
        int index = 0;
        for (String name : before.keySet()) {
            DimensionResult raw = before.get(name);
            DimensionResult clean = after.get(name);
            json.append("    \"").append(name).append("\": {\n");
            result(json, "before", raw, true, 6);
            result(json, "after", clean, true, 6);
            decimalField(json, "delta", clean.score() - raw.score(), false, 6);
            json.append("    }").append(index++ < before.size() - 1 ? "," : "").append('\n');
        }
        json.append("  },\n");

        json.append("  \"method\": {\n");
        field(json, "formula", "score=max(0,100*(1-defectCount/denominator))", true, 4);
        field(json, "Accurate", "Constraint conformance for types, ranges and known categories; no external truth validation.", true, 4);
        field(json, "Complete", "Required-field and empty-line defect rate over all records.", true, 4);
        field(json, "Unique", "Exact duplicate and conflicting business-key variant rate over relationally checked records.", true, 4);
        field(json, "Up-to-date", "Timestamp validity against the documented MovieLens historical coverage, not present-day freshness.", true, 4);
        field(json, "Consistent", "Structural, categorical, cross-table reference and business-key conflict conformance.", false, 4);
        json.append("  },\n");

        json.append("  \"limitations\": [\n");
        stringValue(json, "Correct format does not prove that voluntary user attributes or ratings are factually true.", true, 4);
        stringValue(json, "Historical MovieLens data is assessed against its own documented period and should not be described as current data.", true, 4);
        stringValue(json, "Quarantined records are excluded from the clean version rather than claimed as repaired.", false, 4);
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void boundary(StringBuilder json, String name, long epoch, String usage, boolean comma) {
        json.append("    \"").append(name).append("\": {\n");
        numberField(json, "epochSeconds", epoch, true, 6);
        field(json, "utc", DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epoch)), true, 6);
        field(json, "usage", usage, false, 6);
        json.append("    }").append(comma ? "," : "").append('\n');
    }

    private static void result(StringBuilder json, String label, DimensionResult result,
                               boolean comma, int indent) {
        json.append(" ".repeat(indent)).append('"').append(label).append("\": {\n");
        decimalField(json, "score", result.score(), true, indent + 2);
        numberField(json, "defectCount", result.defects(), true, indent + 2);
        numberField(json, "denominator", result.denominator(), false, indent + 2);
        json.append(" ".repeat(indent)).append('}').append(comma ? "," : "").append('\n');
    }

    private static void field(StringBuilder json, String name, String value, boolean comma, int indent) {
        json.append(" ".repeat(indent)).append('"').append(escape(name)).append("\": \"")
                .append(escape(value)).append('"').append(comma ? "," : "").append('\n');
    }

    private static void stringValue(StringBuilder json, String value, boolean comma, int indent) {
        json.append(" ".repeat(indent)).append('"').append(escape(value)).append('"')
                .append(comma ? "," : "").append('\n');
    }

    private static void numberField(StringBuilder json, String name, long value, boolean comma, int indent) {
        json.append(" ".repeat(indent)).append('"').append(name).append("\": ").append(value)
                .append(comma ? "," : "").append('\n');
    }

    private static void decimalField(StringBuilder json, String name, double value, boolean comma, int indent) {
        json.append(" ".repeat(indent)).append('"').append(name).append("\": ")
                .append(String.format(java.util.Locale.ROOT, "%.2f", value))
                .append(comma ? "," : "").append('\n');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
