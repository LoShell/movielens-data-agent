package com.jhz.movielens.web.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jhz.movielens.agent.protocol.ToolResult;
import com.jhz.movielens.agent.tool.Tool;
import com.jhz.movielens.agent.tool.ToolExecutionContext;
import com.jhz.movielens.web.config.MlProperties;
import com.jhz.movielens.web.config.PipelineProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Each registered instance exposes one independently callable iteration-2 algorithm. */
public final class IterationTwoTool implements Tool {
    private final String name;
    private final String kind;
    private final String algorithm;
    private final MlProperties ml;
    private final PipelineProperties pipeline;
    private final ObjectMapper mapper = new ObjectMapper();

    public IterationTwoTool(String name, String kind, String algorithm,
                            MlProperties ml, PipelineProperties pipeline) {
        this.name = name;
        this.kind = kind;
        this.algorithm = algorithm;
        this.ml = ml;
        this.pipeline = pipeline;
    }

    @Override public String name() { return name; }

    @Override public String description() {
        String action = switch (kind) {
            case "classification" -> "Predict high rating (rating >= 4) for a known user/movie and return validation/test evaluation";
            case "clustering" -> "Assign requested users to interpretable training-period preference groups";
            default -> "Return requested users' reduced coordinates, information retention, clustering comparison and 2D plot";
        };
        return action + " using " + algorithm + ". Use the exact registeredCleanVersion from Runtime context. "
                + "This tool runs the real experiment on the iteration-1 clean HDFS version if needed; "
                + "missing input or models are failures, never simulated results.";
    }

    @Override public JsonNode inputSchema() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("dataVersion").put("type", "string")
                .put("description", "Exact registeredCleanVersion from Runtime context.");
        if (kind.equals("classification")) {
            properties.putObject("userId").put("type", "integer")
                    .put("description", "Optional known user ID; provide with movieId and predictionTime.");
            properties.putObject("movieId").put("type", "integer")
                    .put("description", "Optional known movie ID.");
            properties.putObject("predictionTime").put("type", "integer")
                    .put("description", "Optional UTC Unix seconds after T1; omitted means frozen T1 history.");
        } else {
            properties.putObject("userId").put("type", "integer")
                    .put("description", "Optional target user ID with a training-period profile.");
            properties.putObject("userIds").put("type", "array").put("minItems", 1).put("maxItems", 100)
                    .putObject("items").put("type", "integer");
            properties.putObject("clusters").put("type", "integer").put("minimum", 2).put("maximum", 20)
                    .put("description", "Requested number of user groups; default 5.");
            if (kind.equals("reduction")) {
                properties.putObject("dimension").put("type", "integer").put("minimum", 1)
                        .put("description", "Requested reduced dimensions; default 8.");
                properties.putObject("retention").put("type", "number")
                        .put("description", "Alternative minimum information retention in (0,1].");
                properties.putObject("clusterAlgorithm").put("type", "string")
                        .put("description", "Optional kmeans or gmm; omitted compares both.");
            }
        }
        schema.putArray("required").add("dataVersion");
        schema.put("additionalProperties", false);
        return schema;
    }

    @Override public ToolResult execute(JsonNode input) {
        return execute(input, new ToolExecutionContext(null));
    }

    @Override public ToolResult execute(JsonNode input, ToolExecutionContext context) {
        try {
            String version = input.path("dataVersion").asText("");
            if (!ml.getCleanVersion().equals(version)) {
                return ToolResult.failure("UNREGISTERED_CLEAN_VERSION", "Use registeredCleanVersion exactly.");
            }
            List<String> prediction = new ArrayList<>();
            if (kind.equals("classification")) {
                boolean any = input.has("userId") || input.has("movieId") || input.has("predictionTime");
                if (any) {
                    if (!input.path("userId").canConvertToInt()
                            || !input.path("movieId").canConvertToInt()
                            || (input.has("predictionTime") && !input.path("predictionTime").canConvertToLong())) {
                        return ToolResult.failure("INVALID_PREDICTION", "Provide integer userId and movieId together.");
                    }
                    prediction.addAll(List.of("--user-id", input.get("userId").asText(),
                            "--movie-id", input.get("movieId").asText()));
                    if (input.has("predictionTime")) {
                        prediction.addAll(List.of("--prediction-time", input.get("predictionTime").asText()));
                    }
                }
            } else {
                if (input.has("userId") && input.has("userIds")) {
                    return ToolResult.failure("INVALID_USERS", "Choose userId or userIds.");
                }
                if (input.has("userId")) {
                    if (!input.path("userId").canConvertToInt())
                        return ToolResult.failure("INVALID_USERS", "userId must be an integer.");
                    prediction.addAll(List.of("--user-id", input.get("userId").asText()));
                }
                if (input.has("userIds")) {
                    JsonNode ids = input.get("userIds");
                    if (!ids.isArray() || ids.isEmpty() || ids.size() > 100)
                        return ToolResult.failure("INVALID_USERS", "userIds must contain 1 to 100 integers.");
                    List<String> values = new ArrayList<>();
                    for (JsonNode id : ids) {
                        if (!id.canConvertToInt())
                            return ToolResult.failure("INVALID_USERS", "Every user ID must be an integer.");
                        values.add(id.asText());
                    }
                    prediction.addAll(List.of("--user-ids", String.join(",", values)));
                }
                if (input.has("clusters")) {
                    int count = input.path("clusters").asInt(-1);
                    if (count < 2 || count > 20)
                        return ToolResult.failure("INVALID_CLUSTERS", "clusters must be between 2 and 20.");
                    prediction.addAll(List.of("--clusters", Integer.toString(count)));
                }
                if (kind.equals("reduction")) {
                    if (input.has("dimension") && input.has("retention"))
                        return ToolResult.failure("INVALID_REDUCTION", "Choose dimension or retention.");
                    if (input.has("dimension")) {
                        int dimension = input.path("dimension").asInt(-1);
                        if (dimension < 1 || dimension > 8)
                            return ToolResult.failure("INVALID_REDUCTION", "dimension must be between 1 and 8.");
                        prediction.addAll(List.of("--dimension", Integer.toString(dimension)));
                    }
                    if (input.has("retention")) {
                        double retention = input.path("retention").asDouble(-1);
                        if (retention <= 0 || retention > 1)
                            return ToolResult.failure("INVALID_REDUCTION", "retention must be in (0,1].");
                        prediction.addAll(List.of("--retention", Double.toString(retention)));
                    }
                    if (input.has("clusterAlgorithm")) {
                        String method = input.path("clusterAlgorithm").asText("");
                        if (!method.equals("kmeans") && !method.equals("gmm"))
                            return ToolResult.failure("INVALID_CLUSTER_ALGORITHM", "Use kmeans or gmm.");
                        prediction.addAll(List.of("--cluster-algorithm", method));
                    }
                }
            }

            Path root = Path.of(pipeline.getWorkingDirectory()).toAbsolutePath().normalize();
            Path data = root.resolve("data/processed").resolve(version);
            Path artifact = root.resolve("artifacts/iteration2").resolve(version);
            Path report = artifact.resolve("experiment-report.json");
            Path python = root.resolve(ml.getPython()).normalize();
            if (!python.startsWith(root) || !Files.isRegularFile(python)) {
                return ToolResult.failure("ML_RUNTIME_MISSING", "Configured project-local Python is unavailable: " + python);
            }
            if (!Files.exists(report)) {
                context.progress("ML_PREP", "正在核对迭代一清洗数据版本", name, 15);
                if (!Files.isDirectory(data)) {
                    CommandResult exported = command(List.of("bash", "ml-analysis/export-clean.sh", version), root,
                            Duration.ofMinutes(10));
                    if (exported.code != 0) {
                        return ToolResult.failure("ML_EXPORT_FAILED", exported.output);
                    }
                }
                context.progress("ML_EXEC", "正在运行分类、聚类与降维实验", name, 35);
                CommandResult trained = command(List.of(python.toString(), "ml-analysis/experiment.py", "run",
                        "--data-dir", data.toString(),
                        "--report", "reports/iteration1/agent-20260929-124232-bfc440f2-quality-report.json",
                        "--output-dir", artifact.toString()), root, ml.getTimeout());
                if (trained.code != 0) {
                    return ToolResult.failure("ML_EXPERIMENT_FAILED", trained.output);
                }
            }
            CommandResult published = command(List.of("bash", "ml-analysis/publish-analysis.sh", version),
                    root, Duration.ofMinutes(10));
            if (published.code != 0) {
                return ToolResult.failure("ML_PUBLISH_FAILED", published.output);
            }
            context.progress("ML_EXEC", "正在读取实际模型与评价报告", name, 85);
            List<String> arguments = new ArrayList<>(List.of(python.toString(), "ml-analysis/experiment.py", "inspect",
                    "--output-dir", artifact.toString(), "--kind", kind, "--algorithm", algorithm));
            arguments.addAll(prediction);
            CommandResult inspected = command(arguments, root, Duration.ofMinutes(2));
            if (inspected.code != 0) {
                return ToolResult.failure("ML_INSPECTION_FAILED", inspected.output);
            }
            String jsonLine = inspected.output.lines().reduce((previous, current) -> current).orElse("");
            JsonNode result = mapper.readTree(jsonLine);
            if (!"SUCCEEDED".equals(result.path("status").asText())
                    || !version.equals(result.path("provenance").path("dataVersion").asText())) {
                return ToolResult.failure("ML_VERSION_MISMATCH", "Analysis report and registered clean version disagree.");
            }
            String queryVersion = result.path("queryVersion").asText("");
            if (!queryVersion.matches("[a-f0-9]{16}")) {
                return ToolResult.failure("ML_QUERY_VERSION_MISSING", "Query report has no valid version.");
            }
            CommandResult queryPublished = command(List.of("bash", "ml-analysis/publish-query.sh",
                    version, queryVersion), root, Duration.ofMinutes(2));
            if (queryPublished.code != 0) {
                return ToolResult.failure("ML_QUERY_PUBLISH_FAILED", queryPublished.output);
            }
            ((ObjectNode) result).put("hdfsArtifactRoot", "/movielens/analysis/iteration2/" + version);
            ((ObjectNode) result).put("queryReportPath", "/movielens/analysis/iteration2/"
                    + version + "/queries/" + queryVersion + ".json");
            return ToolResult.success("Actual iteration-2 algorithm result is available.", result);
        } catch (Exception failure) {
            return ToolResult.failure("ML_TOOL_FAILED", failure.getMessage());
        }
    }

    private static CommandResult command(List<String> command, Path root, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            Deque<String> tail = new ArrayDeque<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (tail.size() == 200) tail.removeFirst();
                    tail.addLast(line);
                }
            } catch (Exception failure) {
                tail.addLast(failure.getMessage());
            }
            return String.join(System.lineSeparator(), tail);
        });
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            return new CommandResult(124, "Timed out after " + timeout);
        }
        return new CommandResult(process.exitValue(), output.join());
    }

    private record CommandResult(int code, String output) {}
}
