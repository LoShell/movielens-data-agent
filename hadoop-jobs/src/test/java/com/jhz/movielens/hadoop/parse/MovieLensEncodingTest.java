package com.jhz.movielens.hadoop.parse;

import org.apache.hadoop.conf.Configuration;
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

    @Test
    void decodesCleanedUtf8WhenConfigured() {
        String original = "Amélie (2001)";
        Text hadoopText = new Text(original);
        Configuration configuration = new Configuration(false);
        configuration.set(MovieLensEncoding.CONFIGURATION_KEY, "UTF-8");

        assertEquals(original, MovieLensEncoding.decode(hadoopText, configuration));
    }
}
