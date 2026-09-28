package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.quality.IssueCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserParserTest {
    private final UserParser parser = new UserParser();

    @Test
    void preservesZipCodeLeadingZero() {
        var result = parser.parse("1::F::1::10::01760");

        assertTrue(result.isValid());
        assertEquals("01760", result.value().zipCode());
    }

    @Test
    void flagsUnknownCategoriesWithoutLosingParsedRecord() {
        var result = parser.parse("1::X::99::21::01760");

        assertTrue(result.isParsed());
        assertFalse(result.isValid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == IssueCode.INVALID_GENDER));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == IssueCode.INVALID_AGE_CATEGORY));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.code() == IssueCode.INVALID_OCCUPATION_CATEGORY));
    }
}
