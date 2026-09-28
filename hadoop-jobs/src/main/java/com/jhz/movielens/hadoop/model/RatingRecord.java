package com.jhz.movielens.hadoop.model;

public record RatingRecord(int userId, int movieId, int rating, long timestamp) {
}
