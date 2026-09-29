package com.jhz.movielens.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JsonTreeConverterTest {
    @Test
    void convertsJacksonTwoTreeIntoWebSafeValues() throws Exception {
        var tree = new ObjectMapper().readTree("""
                {
                  "quality": {
                    "dimensions": {
                      "Accurate": {
                        "before": {"score": 95.96},
                        "after": {"score": 100.0},
                        "delta": 4.04
                      }
                    }
                  },
                  "cleaning": {
                    "actions": {
                      "ratings": {"cleanWritten": 894993}
                    }
                  },
                  "cleanRatingSamples": ["1::1::5::978300760"]
                }
                """);

        Map<String, Object> result = new JsonTreeConverter().toWebMap(tree);

        Map<?, ?> quality = assertInstanceOf(Map.class, result.get("quality"));
        Map<?, ?> dimensions = assertInstanceOf(Map.class, quality.get("dimensions"));
        Map<?, ?> accurate = assertInstanceOf(Map.class, dimensions.get("Accurate"));
        Map<?, ?> before = assertInstanceOf(Map.class, accurate.get("before"));
        assertEquals(95.96, ((Number) before.get("score")).doubleValue(), 0.001);

        Map<?, ?> cleaning = assertInstanceOf(Map.class, result.get("cleaning"));
        Map<?, ?> actions = assertInstanceOf(Map.class, cleaning.get("actions"));
        Map<?, ?> ratings = assertInstanceOf(Map.class, actions.get("ratings"));
        assertEquals(894993, ((Number) ratings.get("cleanWritten")).intValue());

        List<?> samples = assertInstanceOf(List.class, result.get("cleanRatingSamples"));
        assertEquals("1::1::5::978300760", samples.get(0));
    }
}
