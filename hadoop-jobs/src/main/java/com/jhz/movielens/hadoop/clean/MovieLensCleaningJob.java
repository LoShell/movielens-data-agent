package com.jhz.movielens.hadoop.clean;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MovieLensCleaningJob extends Configured implements Tool {
    @Override
    public int run(String[] arguments) throws Exception {
        CleaningArguments options = CleaningArguments.parse(arguments);
        FileSystem fileSystem = options.outputRoot().getFileSystem(getConf());
        if (fileSystem.exists(options.outputRoot())) {
            throw new IllegalArgumentException("Output root already exists: " + options.outputRoot());
        }

        Instant startedAt = Instant.now();
        Map<String, Job> jobs = new LinkedHashMap<>();

        Job users = createJob("users", options.users(), new Path(options.outputRoot(), "users"),
                UserCleaningMapper.class, getConf());
        if (!users.waitForCompletion(true)) {
            return 1;
        }
        jobs.put("users", users);

        Job movies = createJob("movies", options.movies(), new Path(options.outputRoot(), "movies"),
                MovieCleaningMapper.class, getConf());
        if (!movies.waitForCompletion(true)) {
            return 1;
        }
        jobs.put("movies", movies);

        Configuration ratingsConfiguration = new Configuration(getConf());
        ratingsConfiguration.set(RatingCleaningMapper.CLEAN_USERS_PATH,
                new Path(options.outputRoot(), "users/clean").toString());
        ratingsConfiguration.set(RatingCleaningMapper.CLEAN_MOVIES_PATH,
                new Path(options.outputRoot(), "movies/clean").toString());
        ratingsConfiguration.setLong(RatingCleaningMapper.MIN_TIMESTAMP, options.minTimestamp());
        ratingsConfiguration.setLong(RatingCleaningMapper.MAX_TIMESTAMP, options.maxTimestamp());
        Job ratings = createJob("ratings", options.ratings(), new Path(options.outputRoot(), "ratings"),
                RatingCleaningMapper.class, ratingsConfiguration);
        if (!ratings.waitForCompletion(true)) {
            return 1;
        }
        jobs.put("ratings", ratings);

        CleaningReportWriter.write(getConf(), options, jobs, startedAt, Instant.now());
        return 0;
    }

    private static Job createJob(String dataset, Path input, Path output,
                                 Class<? extends Mapper> mapperClass, Configuration configuration) throws Exception {
        Configuration jobConfiguration = new Configuration(configuration);
        jobConfiguration.set(CleaningReducer.DATASET_CONFIGURATION, dataset);
        Job job = Job.getInstance(jobConfiguration, "movielens-clean-" + dataset);
        job.setJarByClass(MovieLensCleaningJob.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setMapperClass(mapperClass);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setReducerClass(CleaningReducer.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        job.setNumReduceTasks(1);
        MultipleOutputs.addNamedOutput(job, "clean", TextOutputFormat.class, NullWritable.class, Text.class);
        MultipleOutputs.addNamedOutput(job, "quarantine", TextOutputFormat.class, NullWritable.class, Text.class);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);
        return job;
    }

    public static void main(String[] arguments) throws Exception {
        int exitCode = ToolRunner.run(new MovieLensCleaningJob(), arguments);
        System.exit(exitCode);
    }
}
