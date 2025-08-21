package com.smsanalytics.smstransactionanalyzer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smsanalytics.smstransactionanalyzer.model.CategoryRule
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.ExcludedMessage
import com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache
import com.smsanalytics.smstransactionanalyzer.model.AnalysisMetadata
import com.smsanalytics.smstransactionanalyzer.model.Vendor
import com.smsanalytics.smstransactionanalyzer.model.VendorGroup
import com.smsanalytics.smstransactionanalyzer.model.VendorGroupMember

@Database(
    entities = [Transaction::class, CategoryRule::class, ExcludedMessage::class, SMSAnalysisCache::class, AnalysisMetadata::class, Vendor::class, VendorGroup::class, VendorGroupMember::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SMSDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun excludedMessageDao(): ExcludedMessageDao
    abstract fun smsAnalysisCacheDao(): SMSAnalysisCacheDao
    abstract fun vendorDao(): VendorDao
    abstract fun vendorGroupDao(): VendorGroupDao

    companion object {
        const val DATABASE_NAME = "sms_analytics_db"

        @Volatile
        private var INSTANCE: SMSDatabase? = null

        fun getInstance(context: Context): SMSDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SMSDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
