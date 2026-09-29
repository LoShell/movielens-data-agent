package com.jhz.movielens.web.task;

public record TaskStepSnapshot(String code, String label, Status status) {
    public enum Status {
        PENDING,
        RUNNING,
        DONE,
        FAILED
    }
}
