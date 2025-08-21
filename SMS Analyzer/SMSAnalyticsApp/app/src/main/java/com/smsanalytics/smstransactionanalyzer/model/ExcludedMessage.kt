package com.smsanalytics.smstransactionanalyzer.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "excluded_messages")
data class ExcludedMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: Long, // SMS message ID
    val body: String,
    val sender: String,
    val timestamp: Long,
    val excludedAt: Long = System.currentTimeMillis()
)