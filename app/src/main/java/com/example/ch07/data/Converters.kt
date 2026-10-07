package com.example.ch07.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Room tidak mengenal List<String>, jadi disimpan sebagai teks JSON (Gson).
// ["kuliah", "ide"] <-> "[\"kuliah\",\"ide\"]"
class Converters {

    @TypeConverter
    fun fromTags(tags: List<String>): String = gson.toJson(tags)

    @TypeConverter
    fun toTags(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson<List<String>>(json, type) ?: emptyList()
    }

    private companion object {
        val gson = Gson()
    }
}
