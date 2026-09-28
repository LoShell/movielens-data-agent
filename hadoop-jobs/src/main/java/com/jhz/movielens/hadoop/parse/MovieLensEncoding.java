package com.jhz.movielens.hadoop.parse;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class MovieLensEncoding {
    public static final String CONFIGURATION_KEY = "movielens.input.encoding";

    private MovieLensEncoding() {
    }

    public static String decodeIso88591(Text text) {
        return decode(text, StandardCharsets.ISO_8859_1);
    }

    public static String decode(Text text, Configuration configuration) {
        String charsetName = configuration.get(CONFIGURATION_KEY, StandardCharsets.ISO_8859_1.name());
        return decode(text, Charset.forName(charsetName));
    }

    private static String decode(Text text, Charset charset) {
        if (text == null) {
            return null;
        }
        return new String(text.getBytes(), 0, text.getLength(), charset);
    }
}
