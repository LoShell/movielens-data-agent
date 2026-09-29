package com.jhz.movielens.web.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JsonTreeConverter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> toWebMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("Tool result data must be a JSON object.");
        }
        return objectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize report context.", exception);
        }
    }
}
