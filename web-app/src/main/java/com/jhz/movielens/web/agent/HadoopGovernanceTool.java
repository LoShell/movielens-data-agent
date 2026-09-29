package com.jhz.movielens.web.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jhz.movielens.agent.protocol.ToolResult;
import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolExecutionContext;
import com.jhz.movielens.web.config.PipelineProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HadoopGovernanceTool implements Tool {
    public static final String TOOL_NAME = "run_full_governance";
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PipelineProperties properties;

    public HadoopGovernanceTool(PipelineProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "Run the registered MovieLens iteration-1 Hadoop workflow: raw quality scan, relational scan, "
                + "cleaning/quarantine, clean-data recheck and five-dimension scoring. Use only for a supported "
                + "full governance request and only with identifiers from Runtime context.";
    }

    @Override
    public JsonNode inputSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode propertiesNode = schema.putObject("properties");
        identifierProperty(propertiesNode, "taskId", "Exact taskId from Runtime context.");
        identifierProperty(propertiesNode, "inputVersion", "Exact registeredInputVersion from Runtime context.");
        identifierProperty(propertiesNode, "outputVersion", "Exact reservedOutputVersion from Runtime context.");
        identifierProperty(propertiesNode, "rulesVersion", "Exact registeredRulesVersion from Runtime context.");
        schema.putArray("required")
                .add("taskId").add("inputVersion").add("outputVersion").add("rulesVersion");
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override
    public ToolResult execute(JsonNode input) {
        return execute(input, new ToolExecutionContext(null));
    }

    @Override
    public ToolResult execute(JsonNode input, ToolExecutionContext executionContext) {
        try {
            String taskId = requiredIdentifier(input, "taskId");
            String inputVersion = requiredIdentifier(input, "inputVersion");
            String outputVersion = requiredIdentifier(input, "outputVersion");
            String rulesVersion = requiredIdentifier(input, "rulesVersion");
            if (!properties.getInputVersion().equals(inputVersion)) {
                return ToolResult.failure("UNREGISTERED_INPUT_VERSION",
                        "Input version is not registered: " + inputVersion);
            }
            if (!properties.getRulesVersion().equals(rulesVersion)) {
                return ToolResult.failure("UNREGISTERED_RULES_VERSION",
                        "Rules version is not registered: " + rulesVersion);
            }
            if (!outputVersion.equals("clean-" + taskId)) {
                return ToolResult.failure("INVALID_OUTPUT_VERSION",
                        "Output version must match the reserved task output version.");
            }
            Path workingDirectory = Path.of(properties.getWorkingDirectory()).toAbsolutePath().normalize();
            Path script = workingDirectory.resolve(properties.getScript()).normalize();
            if (!script.startsWith(workingDirectory)) {
                return ToolResult.failure("INVALID_SCRIPT_PATH", "Pipeline script must stay inside the project directory.");
            }

            PipelineProgressParser progressParser = new PipelineProgressParser(executionContext, name());
            CommandResult pipeline = run(List.of(
                    "bash", script.toString(), taskId, inputVersion, outputVersion, rulesVersion),
                    workingDirectory, properties.getTimeout(), progressParser::accept);
            if (pipeline.exitCode() != 0) {
                return ToolResult.failure("HADOOP_PIPELINE_FAILED", pipeline.output());
            }

            String reportRoot = "/movielens/reports/" + taskId;
            JsonNode quality = readHdfsJson(reportRoot + "/five-dimension-quality.json", workingDirectory);
            JsonNode cleaning = readHdfsJson(reportRoot + "/cleaning.json", workingDirectory);
            ObjectNode data = objectMapper.createObjectNode();
            data.put("taskId", taskId);
            data.put("inputVersion", inputVersion);
            data.put("outputVersion", outputVersion);
            data.put("rulesVersion", rulesVersion);
            data.put("reportPath", reportRoot + "/five-dimension-quality.json");
            data.set("quality", quality);
            data.set("cleaning", cleaning);
            String cleanRoot = "/movielens/cleaned/" + outputVersion;
            data.set("cleanRatingSamples", objectMapper.valueToTree(readHdfsLines(
                    cleanRoot + "/ratings/clean/part-r-00000", workingDirectory, 6)));
            data.set("quarantineRatingSamples", objectMapper.valueToTree(readHdfsLines(
                    cleanRoot + "/ratings/quarantine/part-r-00000", workingDirectory, 6)));
            return ToolResult.success("Hadoop pipeline completed successfully.", data);
        } catch (IllegalArgumentException exception) {
            return ToolResult.failure("INVALID_INPUT", exception.getMessage());
        } catch (Exception exception) {
            return ToolResult.failure("HADOOP_TOOL_FAILED", exception.getMessage());
        }
    }

    private JsonNode readHdfsJson(String path, Path workingDirectory) throws Exception {
        CommandResult result = run(List.of("hdfs", "dfs", "-cat", path), workingDirectory, Duration.ofSeconds(30));
        if (result.exitCode() != 0) {
            throw new IOException("Unable to read HDFS report " + path + ": " + result.output());
        }
        return objectMapper.readTree(result.output());
    }

    private static List<String> readHdfsLines(String path, Path workingDirectory, int limit) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("hdfs", "dfs", "-cat", path);
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        List<String> lines = new ArrayList<>(limit);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (lines.size() < limit && (line = reader.readLine()) != null) {
                lines.add(line);
            }
        } finally {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        return lines;
    }

    private static String requiredIdentifier(JsonNode input, String field) {
        String value = input.path(field).asText("");
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + field + ".");
        }
        return value;
    }

    private static void identifierProperty(ObjectNode propertiesNode, String name, String description) {
        propertiesNode.putObject(name)
                .put("type", "string")
                .put("description", description)
                .put("pattern", "^[A-Za-z0-9._-]{1,100}$");
    }

    private static CommandResult run(List<String> command, Path workingDirectory, Duration timeout) throws Exception {
        return run(command, workingDirectory, timeout, ignored -> {
        });
    }

    private static CommandResult run(List<String> command, Path workingDirectory, Duration timeout,
                                     Consumer<String> lineConsumer) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readTail(process, lineConsumer));
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return new CommandResult(124, "Command timed out after " + timeout + ".\n" + output.join());
        }
        return new CommandResult(process.exitValue(), output.join());
    }

    private static String readTail(Process process, Consumer<String> lineConsumer) {
        Deque<String> lines = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineConsumer.accept(line);
                if (lines.size() == 400) {
                    lines.removeFirst();
                }
                lines.addLast(line);
            }
        } catch (IOException exception) {
            lines.addLast("Unable to capture process output: " + exception.getMessage());
        }
        return String.join(System.lineSeparator(), lines);
    }

    private record CommandResult(int exitCode, String output) {
    }

    private static final class PipelineProgressParser {
        private static final Pattern HEADER = Pattern.compile("^\\[(\\d)/5]\\s+.*$");
        private static final Pattern JOB = Pattern.compile(".*Running job:\\s+(job_[0-9_]+).*$");
        private static final Pattern MAP_REDUCE = Pattern.compile(".*map\\s+(\\d+)%\\s+reduce\\s+(\\d+)%.*");
        private static final String[] STAGE_CODES = {
                "", "RAW_QUALITY_SCAN", "RELATIONAL_CHECK", "CLEANING",
                "CLEAN_VALIDATION", "QUALITY_SCORING"
        };
        private static final String[] STAGE_LABELS = {
                "", "正在扫描原始数据质量", "正在检查跨表关系与唯一性", "正在清洗并隔离异常记录",
                "正在复检清洗后的数据", "正在计算五维质量得分"
        };
        private static final int[] START_PROGRESS = {0, 10, 25, 40, 68, 82};
        private static final int[] END_PROGRESS = {0, 25, 40, 68, 82, 93};

        private final ToolExecutionContext context;
        private final String toolName;
        private int stageIndex;
        private int lastBucket = -1;
        private String currentJob = "";

        private PipelineProgressParser(ToolExecutionContext context, String toolName) {
            this.context = context;
            this.toolName = toolName;
        }

        private void accept(String line) {
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                stageIndex = Integer.parseInt(header.group(1));
                lastBucket = -1;
                currentJob = "";
                report(STAGE_LABELS[stageIndex], START_PROGRESS[stageIndex]);
                return;
            }
            if (stageIndex == 0) {
                return;
            }
            Matcher job = JOB.matcher(line);
            if (job.matches()) {
                currentJob = job.group(1);
                lastBucket = -1;
                report(STAGE_LABELS[stageIndex] + " · " + currentJob, START_PROGRESS[stageIndex]);
                return;
            }
            Matcher mapReduce = MAP_REDUCE.matcher(line);
            if (mapReduce.matches()) {
                int map = Integer.parseInt(mapReduce.group(1));
                int reduce = Integer.parseInt(mapReduce.group(2));
                int stagePercent = (map + reduce) / 2;
                int bucket = stagePercent / 10;
                if (bucket == lastBucket) {
                    return;
                }
                lastBucket = bucket;
                int overall = START_PROGRESS[stageIndex]
                        + (END_PROGRESS[stageIndex] - START_PROGRESS[stageIndex]) * stagePercent / 100;
                String detail = STAGE_LABELS[stageIndex]
                        + (currentJob.isBlank() ? "" : " · " + currentJob)
                        + " · Map " + map + "% / Reduce " + reduce + "%";
                report(detail, overall);
            }
        }

        private void report(String message, int progress) {
            context.progress(STAGE_CODES[stageIndex], message, toolName, progress);
        }
    }
}
