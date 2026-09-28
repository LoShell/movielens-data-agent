package com.jhz.movielens.hadoop.model;

public record UserRecord(int userId, String gender, int age, int occupation, String zipCode) {
}
