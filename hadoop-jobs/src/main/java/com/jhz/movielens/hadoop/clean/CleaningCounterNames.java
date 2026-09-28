package com.jhz.movielens.hadoop.clean;

final class CleaningCounterNames {
    static final String GROUP = "movielens.cleaning";

    private CleaningCounterNames() {
    }

    static String action(String dataset, String action) {
        return dataset + "." + action;
    }
}
