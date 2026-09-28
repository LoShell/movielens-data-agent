package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.model.RatingRecord;
import com.jhz.movielens.hadoop.parse.MovieLensEncoding;
import com.jhz.movielens.hadoop.parse.ParseResult;
import com.jhz.movielens.hadoop.parse.RatingParser;
import com.jhz.movielens.hadoop.quality.IssueCode;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.util.Set;

public final class RelationalRatingMapper extends Mapper<LongWritable, Text, Text, Text> {
    static final String USERS_PATH = "movielens.relational.users.path";
    static final String MOVIES_PATH = "movielens.relational.movies.path";
    static final String MIN_TIMESTAMP = "movielens.relational.timestamp.min";
    static final String MAX_TIMESTAMP = "movielens.relational.timestamp.max";

    private final RatingParser parser = new RatingParser();
    private Set<Integer> userIds;
    private Set<Integer> movieIds;
    private long minTimestamp;
    private long maxTimestamp;

    @Override
    protected void setup(Context context) throws IOException {
        String usersPath = context.getConfiguration().get(USERS_PATH);
        String moviesPath = context.getConfiguration().get(MOVIES_PATH);
        if (usersPath == null || moviesPath == null) {
            throw new IOException("Reference dataset paths are not configured.");
        }
        userIds = ReferenceIdLoader.loadUserIds(context.getConfiguration(), new Path(usersPath));
        movieIds = ReferenceIdLoader.loadMovieIds(context.getConfiguration(), new Path(moviesPath));
        minTimestamp = context.getConfiguration().getLong(MIN_TIMESTAMP, -1);
        maxTimestamp = context.getConfiguration().getLong(MAX_TIMESTAMP, -1);
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        ParseResult<RatingRecord> result = parser.parse(MovieLensEncoding.decode(value, context.getConfiguration()));
        if (!result.isValid()) {
            return;
        }

        RatingRecord rating = result.value();
        context.getCounter(QualityCounterNames.RECORD_GROUP,
                QualityCounterNames.record("ratings", "relationallyChecked")).increment(1);

        if (!userIds.contains(rating.userId())) {
            incrementIssue(context, IssueCode.MISSING_USER_REFERENCE);
        }
        if (!movieIds.contains(rating.movieId())) {
            incrementIssue(context, IssueCode.MISSING_MOVIE_REFERENCE);
        }
        if (rating.timestamp() < minTimestamp) {
            incrementIssue(context, IssueCode.TIMESTAMP_BEFORE_EXPECTED_RANGE);
        }
        if (rating.timestamp() > maxTimestamp) {
            incrementIssue(context, IssueCode.TIMESTAMP_AFTER_EXPECTED_RANGE);
        }

        context.write(
                new Text("ratings|" + rating.userId() + '|' + rating.movieId() + '|' + rating.timestamp()),
                new Text(Integer.toString(rating.rating())));
    }

    private void incrementIssue(Context context, IssueCode issueCode) {
        context.getCounter(QualityCounterNames.ISSUE_GROUP,
                QualityCounterNames.issue("ratings", issueCode.name())).increment(1);
    }
}
