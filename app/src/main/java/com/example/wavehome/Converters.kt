package com.example.wavehome

import androidx.room.TypeConverter
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromFloatArray(value: FloatArray): String = value.joinToString(",")

    @TypeConverter
    fun toFloatArray(value: String): FloatArray {
        if (value.isBlank()) return FloatArray(0)
        return value.split(",").map { it.toFloat() }.toFloatArray()
    }

    @TypeConverter
    fun fromSignalMap(value: Map<String, Int>): String {
        val json = JSONObject()
        value.forEach { (key, signal) -> json.put(key, signal) }
        return json.toString()
    }

    @TypeConverter
    fun toSignalMap(value: String): Map<String, Int> {
        if (value.isBlank()) return emptyMap()

        val json = JSONObject(value)
        val result = mutableMapOf<String, Int>()
        json.keys().forEach { key ->
            result[key] = json.getInt(key)
        }
        return result
    }
}
