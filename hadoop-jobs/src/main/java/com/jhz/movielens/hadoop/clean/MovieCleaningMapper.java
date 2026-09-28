package com.jhz.movielens.hadoop.clean;

import com.jhz.movielens.hadoop.model.MovieRecord;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.MovieParser;

public final class MovieCleaningMapper extends AbstractCleaningMapper<MovieRecord> {
    private final MovieParser parser = new MovieParser();

    @Override
    protected String dataset() {
        return "movies";
    }

    @Override
    protected MovieLensLineParser<MovieRecord> parser() {
        return parser;
    }

    @Override
    protected String businessKey(MovieRecord value) {
        return Integer.toString(value.movieId());
    }

    @Override
    protected String canonicalLine(MovieRecord value) {
        return value.movieId() + "::" + value.title() + "::" + String.join("|", value.genres());
    }
}
