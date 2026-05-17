package com.example.notification.adapter.out.persistence.mapper

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

/** Map<String,String> ↔ JSON 직렬화 헬퍼. */
object JsonMapper {

    private val M = ObjectMapper()
    private val MAP_TYPE: TypeReference<Map<String, String>> = object : TypeReference<Map<String, String>>() {}

    @JvmStatic
    fun writeMap(map: Map<String, *>): String =
        try {
            M.writeValueAsString(map)
        } catch (e: JsonProcessingException) {
            throw IllegalArgumentException("json serialize failed", e)
        }

    @JvmStatic
    fun readStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            M.readValue(json, MAP_TYPE)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("json parse failed: $json", e)
        }
    }

    @JvmStatic
    fun <T : Any> readValue(json: String, type: Class<T>): T =
        try {
            M.readValue(json, type)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("json parse failed: $json", e)
        }

    @JvmStatic
    fun <T> readValue(json: String, type: TypeReference<T>): T =
        try {
            M.readValue(json, type)
        } catch (e: JsonProcessingException) {
            throw IllegalStateException("json parse failed: $json", e)
        }

    @JvmStatic
    fun objectMapper(): ObjectMapper = M
}
