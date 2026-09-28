package com.jhz.movielens.web.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jhz.movielens.agent.protocol.ToolResult;
import com.jhz.movielens.agent.tool.Tool;
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
import java.util.regex.Pattern;

@Component
public class HadoopGovernanceTool implements Tool {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private final ObjectMapper objectMapper;
    private final PipelineProperties properties;

    public HadoopGovernanceTool(ObjectMapper objectMapper, PipelineProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public String name() {
        return WorkflowPlanner.TOOL_NAME;
    }

    @Override
    public String description() {
        return "Runs the fixed MovieLens Hadoop scan-clean-rescan-score pipeline and returns HDFS reports.";
    }

    @Override
    public ToolResult execute(JsonNode input) {
        try {
            String taskId = requiredIdentifier(input, "taskId");
            String inputVersion = requiredIdentifier(input, "inputVersion");
            String outputVersion = requiredIdentifier(input, "outputVersion");
            String rulesVersion = requiredIdentifier(input, "rulesVersion");
            Path workingDirectory = Path.of(properties.getWorkingDirectory()).toAbsolutePath().normalize();
            Path script = workingDirectory.resolve(properties.getScript()).normalize();
            if (!script.startsWith(workingDirectory)) {
                return ToolResult.failure("INVALID_SCRIPT_PATH", "Pipeline script must stay inside the project directory.");
            }

            CommandResult pipeline = run(List.of(
                    "bash", script.toString(), taskId, inputVersion, outputVersion, rulesVersion),
                    workingDirectory, properties.getTimeout());
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

    private static String requiredIdentifier(JsonNode input, String field) {
        String value = input.path(field).asText("");
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + field + ".");
        }
        return value;
    }

    private static CommandResult run(List<String> command, Path workingDirectory, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readTail(process));
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return new CommandResult(124, "Command timed out after " + timeout + ".\n" + output.join());
        }
        return new CommandResult(process.exitValue(), output.join());
    }

    private static String readTail(Process process) {
        Deque<String> lines = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
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
}
