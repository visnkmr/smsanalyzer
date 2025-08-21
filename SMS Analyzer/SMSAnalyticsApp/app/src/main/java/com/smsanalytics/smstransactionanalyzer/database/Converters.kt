package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {

    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(it, type)
        }
    }

    @TypeConverter
    fun fromTransactionType(value: com.smsanalytics.smstransactionanalyzer.model.TransactionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTransactionType(value: String?): com.smsanalytics.smstransactionanalyzer.model.TransactionType? {
        return value?.let { com.smsanalytics.smstransactionanalyzer.model.TransactionType.valueOf(it) }
    }

    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun toDate(timestamp: Long?): Date? {
        return timestamp?.let { Date(it) }
    }
}
