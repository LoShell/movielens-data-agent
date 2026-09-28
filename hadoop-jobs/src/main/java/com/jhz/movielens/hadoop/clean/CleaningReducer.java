package com.jhz.movielens.hadoop.clean;

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class CleaningReducer extends Reducer<Text, Text, NullWritable, Text> {
    static final String DATASET_CONFIGURATION = "movielens.cleaning.dataset";
    private MultipleOutputs<NullWritable, Text> outputs;
    private String dataset;

    @Override
    protected void setup(Context context) {
        outputs = new MultipleOutputs<>(context);
        dataset = context.getConfiguration().get(DATASET_CONFIGURATION);
    }

    @Override
    protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
        List<CleaningValue> copied = new ArrayList<>();
        for (Text value : values) {
            copied.add(CleaningValue.decode(value.toString()));
        }

        if (key.toString().startsWith("invalid|")) {
            for (CleaningValue value : copied) {
                quarantine(value.reason(), value.line());
            }
            return;
        }

        List<String> lines = copied.stream().map(CleaningValue::line).toList();
        CleaningGroupDecision decision = CleaningGroupDecision.decide(lines);
        if (decision.cleanLine() != null) {
            outputs.write("clean", NullWritable.get(), new Text(decision.cleanLine()), "clean/part");
            context.getCounter(CleaningCounterNames.GROUP,
                    CleaningCounterNames.action(dataset, "cleanWritten")).increment(1);
            for (String duplicate : decision.quarantinedLines()) {
                quarantine("DUPLICATE_RECORD", duplicate);
            }
            if (decision.duplicatesRemoved() > 0) {
                context.getCounter(CleaningCounterNames.GROUP,
                        CleaningCounterNames.action(dataset, "duplicatesRemoved"))
                        .increment(decision.duplicatesRemoved());
            }
        } else {
            for (String conflicting : decision.quarantinedLines()) {
                quarantine("CONFLICTING_RECORD", conflicting);
            }
            context.getCounter(CleaningCounterNames.GROUP,
                    CleaningCounterNames.action(dataset, "conflictsQuarantined"))
                    .increment(decision.conflictsQuarantined());
        }
    }

    private void quarantine(String reason, String line) throws IOException, InterruptedException {
        outputs.write("quarantine", NullWritable.get(), new Text(reason + '\t' + line), "quarantine/part");
    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        outputs.close();
    }
}
