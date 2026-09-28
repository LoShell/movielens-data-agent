package com.jhz.movielens.hadoop.score;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlledJsonTest {
    @Test
    void readsNestedObjectWithoutStoppingAtInnerBrace() {
        String json = "{\"records\":{\"ratings\":{\"total\":12},\"users\":{\"total\":3}}}";
        String records = ControlledJson.object(json, "records");
        assertEquals(12, ControlledJson.longValue(ControlledJson.object(records, "ratings"), "total"));
        assertEquals(3, ControlledJson.longValue(ControlledJson.object(records, "users"), "total"));
    }
}
