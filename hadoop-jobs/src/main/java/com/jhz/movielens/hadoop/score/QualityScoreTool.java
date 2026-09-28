package com.jhz.movielens.hadoop.score;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class QualityScoreTool extends Configured implements Tool {
    @Override
    public int run(String[] arguments) throws Exception {
        QualityScoreArguments options = QualityScoreArguments.parse(arguments);
        QualitySnapshot before = new QualitySnapshot(
                read(options.rawBasic()), read(options.rawRelational()));
        QualitySnapshot after = new QualitySnapshot(
                read(options.cleanBasic()), read(options.cleanRelational()));

        Map<String, DimensionResult> beforeScores = FiveDimensionScorer.score(before);
        Map<String, DimensionResult> afterScores = FiveDimensionScorer.score(after);
        QualityScoreReportWriter.write(getConf(), options, beforeScores, afterScores);
        return 0;
    }

    private String read(Path path) throws IOException {
        FileSystem fileSystem = path.getFileSystem(getConf());
        if (!fileSystem.exists(path)) {
            throw new IOException("Quality input report does not exist: " + path);
        }
        try (FSDataInputStream input = fileSystem.open(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] arguments) throws Exception {
        int exitCode = ToolRunner.run(new QualityScoreTool(), arguments);
        System.exit(exitCode);
    }
}
