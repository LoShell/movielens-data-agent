package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.quality.IssueCode;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class IdentityConflictReducer extends Reducer<Text, Text, NullWritable, NullWritable> {
    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        String dataset = datasetName(key.toString());
        List<String> copiedValues = new ArrayList<>();
        for (Text value : values) {
            copiedValues.add(value.toString());
        }
        RecordGroupAnalysis analysis = RecordGroupAnalysis.analyze(copiedValues);
        if (analysis.duplicateRecords() > 0) {
            context.getCounter(QualityCounterNames.ISSUE_GROUP,
                    QualityCounterNames.issue(dataset, IssueCode.DUPLICATE_RECORD.name()))
                    .increment(analysis.duplicateRecords());
        }
        if (analysis.conflictingRecords() > 0) {
            context.getCounter(QualityCounterNames.ISSUE_GROUP,
                    QualityCounterNames.issue(dataset, IssueCode.CONFLICTING_RECORD.name()))
                    .increment(analysis.conflictingRecords());
        }
    }

    private static String datasetName(String key) throws IOException {
        int separator = key.indexOf('|');
        if (separator <= 0) {
            throw new IOException("Invalid grouped identity key: " + key);
        }
        return key.substring(0, separator);
    }
}
