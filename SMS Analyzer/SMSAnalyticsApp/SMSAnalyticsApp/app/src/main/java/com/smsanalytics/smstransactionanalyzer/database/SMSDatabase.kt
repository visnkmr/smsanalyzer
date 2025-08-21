package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smsanalytics.smstransactionanalyzer.model.CategoryRule
import com.smsanalytics.smstransactionanalyzer.model.Transaction

@Database(
    entities = [Transaction::class, CategoryRule::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SMSDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        const val DATABASE_NAME = "sms_analytics_db"
    }
}
