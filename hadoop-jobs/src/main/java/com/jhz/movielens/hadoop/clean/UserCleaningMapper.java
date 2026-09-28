package com.jhz.movielens.hadoop.clean;

import com.jhz.movielens.hadoop.model.UserRecord;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.UserParser;

public final class UserCleaningMapper extends AbstractCleaningMapper<UserRecord> {
    private final UserParser parser = new UserParser();

    @Override
    protected String dataset() {
        return "users";
    }

    @Override
    protected MovieLensLineParser<UserRecord> parser() {
        return parser;
    }

    @Override
    protected String businessKey(UserRecord value) {
        return Integer.toString(value.userId());
    }

    @Override
    protected String canonicalLine(UserRecord value) {
        return value.userId() + "::" + value.gender() + "::" + value.age() + "::"
                + value.occupation() + "::" + value.zipCode();
    }
}
