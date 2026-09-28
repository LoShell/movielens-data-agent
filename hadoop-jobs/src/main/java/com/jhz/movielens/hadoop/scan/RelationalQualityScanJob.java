package com.jhz.movielens.hadoop.scan;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.time.Instant;

public final class RelationalQualityScanJob extends Configured implements Tool {
    @Override
    public int run(String[] arguments) throws Exception {
        RelationalQualityScanArguments options = RelationalQualityScanArguments.parse(arguments);
        Instant startedAt = Instant.now();

        Configuration configuration = getConf();
        configuration.set(RelationalRatingMapper.USERS_PATH, options.users().toString());
        configuration.set(RelationalRatingMapper.MOVIES_PATH, options.movies().toString());
        configuration.setLong(RelationalRatingMapper.MIN_TIMESTAMP, options.minTimestamp());
        configuration.setLong(RelationalRatingMapper.MAX_TIMESTAMP, options.maxTimestamp());

        Job job = Job.getInstance(configuration, "movielens-relational-quality-scan-" + options.taskId());
        job.setJarByClass(RelationalQualityScanJob.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setReducerClass(IdentityConflictReducer.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(NullWritable.class);
        job.setOutputFormatClass(NullOutputFormat.class);
        job.setNumReduceTasks(1);

        MultipleInputs.addInputPath(job, options.ratings(), TextInputFormat.class, RelationalRatingMapper.class);
        MultipleInputs.addInputPath(job, options.users(), TextInputFormat.class, UserIdentityMapper.class);
        MultipleInputs.addInputPath(job, options.movies(), TextInputFormat.class, MovieIdentityMapper.class);

        boolean succeeded = job.waitForCompletion(true);
        if (!succeeded) {
            return 1;
        }
        RelationalQualityReportWriter.write(configuration, options, job, startedAt, Instant.now());
        return 0;
    }

    public static void main(String[] arguments) throws Exception {
        int exitCode = ToolRunner.run(new RelationalQualityScanJob(), arguments);
        System.exit(exitCode);
    }
}
