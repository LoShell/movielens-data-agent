package com.jhz.movielens.hadoop.clean;

import com.jhz.movielens.hadoop.model.RatingRecord;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.RatingParser;
import org.apache.hadoop.fs.Path;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RatingCleaningMapper extends AbstractCleaningMapper<RatingRecord> {
    static final String CLEAN_USERS_PATH = "movielens.cleaning.users.path";
    static final String CLEAN_MOVIES_PATH = "movielens.cleaning.movies.path";
    static final String MIN_TIMESTAMP = "movielens.cleaning.timestamp.min";
    static final String MAX_TIMESTAMP = "movielens.cleaning.timestamp.max";

    private final RatingParser parser = new RatingParser();
    private Set<Integer> userIds;
    private Set<Integer> movieIds;
    private long minTimestamp;
    private long maxTimestamp;

    @Override
    protected void setup(Context context) throws IOException {
        userIds = CleanReferenceIdLoader.load(context.getConfiguration(),
                new Path(context.getConfiguration().get(CLEAN_USERS_PATH)));
        movieIds = CleanReferenceIdLoader.load(context.getConfiguration(),
                new Path(context.getConfiguration().get(CLEAN_MOVIES_PATH)));
        minTimestamp = context.getConfiguration().getLong(MIN_TIMESTAMP, -1);
        maxTimestamp = context.getConfiguration().getLong(MAX_TIMESTAMP, -1);
    }

    @Override
    protected String dataset() {
        return "ratings";
    }

    @Override
    protected MovieLensLineParser<RatingRecord> parser() {
        return parser;
    }

    @Override
    protected String businessKey(RatingRecord value) {
        return value.userId() + "|" + value.movieId() + "|" + value.timestamp();
    }

    @Override
    protected String canonicalLine(RatingRecord value) {
        return value.userId() + "::" + value.movieId() + "::" + value.rating() + "::" + value.timestamp();
    }

    @Override
    protected String additionalInvalidReason(RatingRecord value) {
        List<String> reasons = new ArrayList<>();
        if (!userIds.contains(value.userId())) {
            reasons.add("MISSING_CLEAN_USER_REFERENCE");
        }
        if (!movieIds.contains(value.movieId())) {
            reasons.add("MISSING_CLEAN_MOVIE_REFERENCE");
        }
        if (value.timestamp() < minTimestamp) {
            reasons.add("TIMESTAMP_BEFORE_EXPECTED_RANGE");
        }
        if (value.timestamp() > maxTimestamp) {
            reasons.add("TIMESTAMP_AFTER_EXPECTED_RANGE");
        }
        return String.join("|", reasons);
    }
}
