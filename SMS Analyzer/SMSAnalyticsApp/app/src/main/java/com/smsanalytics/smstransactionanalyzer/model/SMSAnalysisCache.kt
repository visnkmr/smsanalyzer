package com.smsanalytics.smstransactionanalyzer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smsanalytics.smstransactionanalyzer.model.TransactionType

@Entity(tableName = "sms_analysis_cache")
data class SMSAnalysisCache(
    @PrimaryKey
    val messageId: Long,
    val messageBody: String,
    val sender: String,
    val timestamp: Long,
    val hasTransaction: Boolean,
    val transactionAmount: Double?,
    val transactionType: TransactionType?,
    val processedAt: Long = System.currentTimeMillis(),
    val isExcluded: Boolean? = false
)

@Entity(tableName = "analysis_metadata")
data class AnalysisMetadata(
    @PrimaryKey
    val id: String = "last_analysis",
    val lastProcessedMessageId: Long,
    val lastProcessedTimestamp: Long,
    val totalMessagesProcessed: Int,
    val lastAnalysisDate: Long = System.currentTimeMillis()
)