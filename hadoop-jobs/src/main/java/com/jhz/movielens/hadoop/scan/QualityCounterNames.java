package com.jhz.movielens.hadoop.scan;

public final class QualityCounterNames {
    public static final String RECORD_GROUP = "movielens.records";
    public static final String ISSUE_GROUP = "movielens.issues";

    private QualityCounterNames() {
    }

    public static String record(String dataset, String metric) {
        return dataset + "." + metric;
    }

    public static String issue(String dataset, String issueCode) {
        return dataset + "." + issueCode;
    }
}
