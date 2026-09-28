package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.quality.IssueCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovieParserTest {
    private final MovieParser parser = new MovieParser();

    @Test
    void parsesTitleAndMultipleGenres() {
        var result = parser.parse("1::Toy Story (1995)::Animation|Children's|Comedy");

        assertTrue(result.isValid());
        assertEquals("Toy Story (1995)", result.value().title());
        assertEquals(List.of("Animation", "Children's", "Comedy"), result.value().genres());
    }

    @Test
    void flagsDuplicateAndUnknownGenres() {
        var result = parser.parse("1::Example (2000)::Drama|Drama|Made-Up");

        assertTrue(result.isParsed());
        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == IssueCode.DUPLICATE_GENRE));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == IssueCode.UNKNOWN_GENRE));
    }
}
