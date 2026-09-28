package com.jhz.movielens.hadoop.clean;

import com.jhz.movielens.hadoop.parse.MovieLensEncoding;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.ParseResult;
import com.jhz.movielens.hadoop.quality.QualityIssue;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.util.stream.Collectors;

abstract class AbstractCleaningMapper<T> extends Mapper<LongWritable, Text, Text, Text> {
    protected abstract String dataset();

    protected abstract MovieLensLineParser<T> parser();

    protected abstract String businessKey(T value);

    protected abstract String canonicalLine(T value);

    protected String additionalInvalidReason(T value) {
        return "";
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        String line = MovieLensEncoding.decodeIso88591(value);
        ParseResult<T> result = parser().parse(line);
        String parseReason = result.issues().stream()
                .filter(issue -> issue.severity() == com.jhz.movielens.hadoop.quality.IssueSeverity.ERROR)
                .map(QualityIssue::code)
                .map(Enum::name)
                .distinct()
                .collect(Collectors.joining("|"));

        if (!result.isValid()) {
            emitInvalid(key, line, parseReason.isEmpty() ? "UNPARSEABLE" : parseReason, context);
            return;
        }

        T parsed = result.value();
        String additionalReason = additionalInvalidReason(parsed);
        if (!additionalReason.isEmpty()) {
            emitInvalid(key, line, additionalReason, context);
            return;
        }

        context.write(new Text("valid|" + businessKey(parsed)),
                new Text(CleaningValue.valid(canonicalLine(parsed)).encode()));
    }

    private void emitInvalid(LongWritable key, String line, String reason, Context context)
            throws IOException, InterruptedException {
        context.getCounter(CleaningCounterNames.GROUP,
                CleaningCounterNames.action(dataset(), "invalidQuarantined")).increment(1);
        context.write(new Text("invalid|" + key.get()), new Text(CleaningValue.invalid(reason, line).encode()));
    }
}
