package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.quality.IssueCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RatingParserTest {
    private final RatingParser parser = new RatingParser();

    @Test
    void parsesValidRating() {
        ParseResult<?> result = parser.parse("1::1193::5::978300760");

        assertTrue(result.isValid());
        assertEquals(1, parser.parse("1::1193::5::978300760").value().userId());
    }

    @Test
    void keepsTypedRecordButFlagsOutOfRangeRating() {
        var result = parser.parse("1::1193::6::978300760");

        assertTrue(result.isParsed());
        assertFalse(result.isValid());
        assertEquals(6, result.value().rating());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == IssueCode.RATING_OUT_OF_RANGE));
    }

    @Test
    void rejectsMalformedNumericField() {
        var result = parser.parse("user::1193::5::978300760");

        assertFalse(result.isParsed());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == IssueCode.INVALID_INTEGER));
    }
}
