package com.jhz.movielens.hadoop.score;

import java.util.List;

record QualitySnapshot(String basicReport, String relationalReport) {
    private static final List<String> DATASETS = List.of("ratings", "users", "movies");

    long totalRecords() {
        return DATASETS.stream().mapToLong(dataset -> basicRecord(dataset, "total")).sum();
    }

    long ratingRecords() {
        return basicRecord("ratings", "total");
    }

    long relationallyCheckedRecords() {
        return DATASETS.stream().mapToLong(dataset -> relationalRecord(dataset, "relationallyChecked")).sum();
    }

    long basicIssues(String... names) {
        long total = 0;
        for (String dataset : DATASETS) {
            String issues = ControlledJson.object(ControlledJson.object(basicReport, "issues"), dataset);
            for (String name : names) {
                total += ControlledJson.longValue(issues, name);
            }
        }
        return total;
    }

    long relationalIssues(String... names) {
        long total = 0;
        for (String dataset : DATASETS) {
            String issues = ControlledJson.object(ControlledJson.object(relationalReport, "issues"), dataset);
            for (String name : names) {
                total += ControlledJson.longValue(issues, name);
            }
        }
        return total;
    }

    private long basicRecord(String dataset, String metric) {
        String records = ControlledJson.object(basicReport, "records");
        return ControlledJson.longValue(ControlledJson.object(records, dataset), metric);
    }

    private long relationalRecord(String dataset, String metric) {
        String records = ControlledJson.object(relationalReport, "records");
        return ControlledJson.longValue(ControlledJson.object(records, dataset), metric);
    }
}
