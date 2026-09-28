package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.model.RatingRecord;
import com.jhz.movielens.hadoop.quality.IssueCode;
import com.jhz.movielens.hadoop.quality.QualityIssue;

import java.util.ArrayList;
import java.util.List;

public final class RatingParser implements MovieLensLineParser<RatingRecord> {
    @Override
    public ParseResult<RatingRecord> parse(String line) {
        List<QualityIssue> issues = new ArrayList<>();
        String[] fields = ParserSupport.split(line, 4, issues);
        if (fields == null) {
            return new ParseResult<>(null, issues);
        }

        Integer userId = ParserSupport.integer(fields[0], "userId", issues);
        Integer movieId = ParserSupport.integer(fields[1], "movieId", issues);
        Integer rating = ParserSupport.integer(fields[2], "rating", issues);
        Long timestamp = ParserSupport.longValue(fields[3], "timestamp", issues);

        if (userId == null || movieId == null || rating == null || timestamp == null) {
            return new ParseResult<>(null, issues);
        }

        ParserSupport.requirePositiveIdentifier(userId, "userId", issues);
        ParserSupport.requirePositiveIdentifier(movieId, "movieId", issues);
        if (rating < 1 || rating > 5) {
            issues.add(QualityIssue.error(
                    IssueCode.RATING_OUT_OF_RANGE, "rating", "Rating must be between 1 and 5."));
        }
        if (timestamp <= 0) {
            issues.add(QualityIssue.error(
                    IssueCode.NON_POSITIVE_TIMESTAMP, "timestamp", "Timestamp must be positive."));
        }

        return new ParseResult<>(new RatingRecord(userId, movieId, rating, timestamp), issues);
    }
}
