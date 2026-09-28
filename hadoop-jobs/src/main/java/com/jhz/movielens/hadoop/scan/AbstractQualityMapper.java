package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.parse.MovieLensEncoding;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.ParseResult;
import com.jhz.movielens.hadoop.quality.QualityIssue;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

abstract class AbstractQualityMapper<T>
        extends Mapper<LongWritable, Text, NullWritable, NullWritable> {

    protected abstract String datasetName();

    protected abstract MovieLensLineParser<T> parser();

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        String dataset = datasetName();
        context.getCounter(QualityCounterNames.RECORD_GROUP,
                QualityCounterNames.record(dataset, "total")).increment(1);

        ParseResult<T> result = parser().parse(MovieLensEncoding.decode(value, context.getConfiguration()));
        if (result.isParsed()) {
            context.getCounter(QualityCounterNames.RECORD_GROUP,
                    QualityCounterNames.record(dataset, "parsed")).increment(1);
        }
        if (result.isValid()) {
            context.getCounter(QualityCounterNames.RECORD_GROUP,
                    QualityCounterNames.record(dataset, "valid")).increment(1);
        } else {
            context.getCounter(QualityCounterNames.RECORD_GROUP,
                    QualityCounterNames.record(dataset, "invalid")).increment(1);
        }

        for (QualityIssue issue : result.issues()) {
            context.getCounter(QualityCounterNames.ISSUE_GROUP,
                    QualityCounterNames.issue(dataset, issue.code().name())).increment(1);
        }
    }
}
