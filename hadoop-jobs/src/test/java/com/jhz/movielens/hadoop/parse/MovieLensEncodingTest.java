package com.jhz.movielens.hadoop.parse;

import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MovieLensEncodingTest {
    @Test
    void decodesOriginalIso88591BytesWithoutReplacementCharacters() {
        String original = "Amélie (2001)";
        Text hadoopText = new Text();
        hadoopText.set(original.getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(original, MovieLensEncoding.decodeIso88591(hadoopText));
    }
}
