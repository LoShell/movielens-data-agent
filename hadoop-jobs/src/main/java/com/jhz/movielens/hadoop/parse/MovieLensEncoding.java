package com.jhz.movielens.hadoop.parse;

import org.apache.hadoop.io.Text;

import java.nio.charset.StandardCharsets;

public final class MovieLensEncoding {
    private MovieLensEncoding() {
    }

    public static String decodeIso88591(Text text) {
        if (text == null) {
            return null;
        }
        return new String(text.getBytes(), 0, text.getLength(), StandardCharsets.ISO_8859_1);
    }
}
