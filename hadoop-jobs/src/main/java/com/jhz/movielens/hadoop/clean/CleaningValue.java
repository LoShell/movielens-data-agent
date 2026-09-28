package com.jhz.movielens.hadoop.clean;

record CleaningValue(boolean valid, String reason, String line) {
    private static final char SEPARATOR = '\u0001';

    static CleaningValue valid(String line) {
        return new CleaningValue(true, "", line);
    }

    static CleaningValue invalid(String reason, String line) {
        return new CleaningValue(false, reason, line);
    }

    String encode() {
        return (valid ? "V" : "I") + SEPARATOR + reason + SEPARATOR + line;
    }

    static CleaningValue decode(String encoded) {
        String[] parts = encoded.split(String.valueOf(SEPARATOR), 3);
        if (parts.length != 3 || !(parts[0].equals("V") || parts[0].equals("I"))) {
            throw new IllegalArgumentException("Invalid cleaning mapper value.");
        }
        return new CleaningValue(parts[0].equals("V"), parts[1], parts[2]);
    }
}
