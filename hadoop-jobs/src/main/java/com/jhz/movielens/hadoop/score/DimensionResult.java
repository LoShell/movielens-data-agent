package com.jhz.movielens.hadoop.score;

record DimensionResult(String name, long defects, long denominator, double score) {
    static DimensionResult of(String name, long defects, long denominator) {
        double score = denominator == 0 ? 0.0
                : Math.max(0.0, Math.min(100.0, 100.0 * (1.0 - (double) defects / denominator)));
        return new DimensionResult(name, defects, denominator, score);
    }
}
