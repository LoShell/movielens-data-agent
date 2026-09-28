package com.jhz.movielens.hadoop.parse;

@FunctionalInterface
public interface MovieLensLineParser<T> {
    ParseResult<T> parse(String line);
}
