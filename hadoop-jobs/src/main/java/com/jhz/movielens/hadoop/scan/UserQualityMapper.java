package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.model.UserRecord;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.UserParser;

public final class UserQualityMapper extends AbstractQualityMapper<UserRecord> {
    private final UserParser parser = new UserParser();

    @Override
    protected String datasetName() {
        return "users";
    }

    @Override
    protected MovieLensLineParser<UserRecord> parser() {
        return parser;
    }
}
