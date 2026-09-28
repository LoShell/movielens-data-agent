package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.model.RatingRecord;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.RatingParser;

public final class RatingQualityMapper extends AbstractQualityMapper<RatingRecord> {
    private final RatingParser parser = new RatingParser();

    @Override
    protected String datasetName() {
        return "ratings";
    }

    @Override
    protected MovieLensLineParser<RatingRecord> parser() {
        return parser;
    }
}
