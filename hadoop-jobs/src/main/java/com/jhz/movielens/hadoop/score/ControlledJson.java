package com.jhz.movielens.hadoop.score;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ControlledJson {
    private ControlledJson() {
    }

    static String object(String json, String name) {
        int nameIndex = json.indexOf('"' + name + '"');
        if (nameIndex < 0) {
            throw new IllegalArgumentException("Missing JSON object: " + name);
        }
        int start = json.indexOf('{', nameIndex + name.length() + 2);
        if (start < 0) {
            throw new IllegalArgumentException("JSON value is not an object: " + name);
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    quoted = false;
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return json.substring(start, index + 1);
            }
        }
        throw new IllegalArgumentException("Unclosed JSON object: " + name);
    }

    static long longValue(String jsonObject, String name) {
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(jsonObject);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing numeric JSON field: " + name);
        }
        return Long.parseLong(matcher.group(1));
    }
}
