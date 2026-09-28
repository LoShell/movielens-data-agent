package com.jhz.movielens.hadoop.model;

import java.util.List;

public record MovieRecord(int movieId, String title, List<String> genres) {
    public MovieRecord {
        genres = List.copyOf(genres);
    }
}
