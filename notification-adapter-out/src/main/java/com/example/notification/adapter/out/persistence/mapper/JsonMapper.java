package com.example.notification.adapter.out.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Map<String,String> ↔ JSON 직렬화 헬퍼. */
public final class JsonMapper {

    private static final ObjectMapper M = new ObjectMapper();
    private static final TypeReference<java.util.Map<String, String>> MAP_TYPE =
            new TypeReference<>() {};

    private JsonMapper() {}

    public static String writeMap(java.util.Map<String, ?> map) {
        try {
            return M.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("json serialize failed", e);
        }
    }

    public static java.util.Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) return java.util.Map.of();
        try {
            return M.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json parse failed: " + json, e);
        }
    }

    public static <T> T readValue(String json, Class<T> type) {
        try {
            return M.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json parse failed: " + json, e);
        }
    }

    public static <T> T readValue(String json, TypeReference<T> type) {
        try {
            return M.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("json parse failed: " + json, e);
        }
    }

    public static ObjectMapper objectMapper() {
        return M;
    }
}
