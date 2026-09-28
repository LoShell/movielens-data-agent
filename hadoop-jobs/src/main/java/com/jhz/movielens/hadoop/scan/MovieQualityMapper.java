package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.model.MovieRecord;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.MovieParser;

public final class MovieQualityMapper extends AbstractQualityMapper<MovieRecord> {
    private final MovieParser parser = new MovieParser();

    @Override
    protected String datasetName() {
        return "movies";
    }

    @Override
    protected MovieLensLineParser<MovieRecord> parser() {
        return parser;
    }
}
