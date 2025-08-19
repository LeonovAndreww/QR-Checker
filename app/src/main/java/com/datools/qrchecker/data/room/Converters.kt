package com.datools.qrchecker.data.room

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return gson.toJson(list)
    }

    @TypeConverter
    fun toList(value: String?): List<String>? {
        return value?.let {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(it, listType)
        }
    }
}