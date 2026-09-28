package com.jhz.movielens.hadoop.scan;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.time.Instant;

public final class RawQualityScanJob extends Configured implements Tool {
    @Override
    public int run(String[] arguments) throws Exception {
        RawQualityScanArguments options = RawQualityScanArguments.parse(arguments);
        Instant startedAt = Instant.now();

        Job job = Job.getInstance(getConf(), "movielens-raw-quality-scan-" + options.taskId());
        job.setJarByClass(RawQualityScanJob.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(NullWritable.class);
        job.setOutputFormatClass(NullOutputFormat.class);
        job.setNumReduceTasks(0);

        MultipleInputs.addInputPath(job, options.ratings(), TextInputFormat.class, RatingQualityMapper.class);
        MultipleInputs.addInputPath(job, options.users(), TextInputFormat.class, UserQualityMapper.class);
        MultipleInputs.addInputPath(job, options.movies(), TextInputFormat.class, MovieQualityMapper.class);

        boolean succeeded = job.waitForCompletion(true);
        if (!succeeded) {
            return 1;
        }

        RawQualityReportWriter.write(getConf(), options, job, startedAt, Instant.now());
        return 0;
    }

    public static void main(String[] arguments) throws Exception {
        int exitCode = ToolRunner.run(new RawQualityScanJob(), arguments);
        System.exit(exitCode);
    }
}
