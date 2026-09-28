package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.model.MovieRecord;
import com.jhz.movielens.hadoop.parse.MovieLensEncoding;
import com.jhz.movielens.hadoop.parse.MovieParser;
import com.jhz.movielens.hadoop.parse.ParseResult;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public final class MovieIdentityMapper extends Mapper<LongWritable, Text, Text, Text> {
    private final MovieParser parser = new MovieParser();

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        ParseResult<MovieRecord> result = parser.parse(MovieLensEncoding.decodeIso88591(value));
        if (!result.isValid()) {
            return;
        }
        MovieRecord movie = result.value();
        context.getCounter(QualityCounterNames.RECORD_GROUP,
                QualityCounterNames.record("movies", "relationallyChecked")).increment(1);
        context.write(
                new Text("movies|" + movie.movieId()),
                new Text(movie.title() + '|' + String.join("|", movie.genres())));
    }
}
