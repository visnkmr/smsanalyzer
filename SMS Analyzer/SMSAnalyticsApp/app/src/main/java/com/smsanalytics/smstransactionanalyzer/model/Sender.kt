package com.smsanalytics.smstransactionanalyzer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "senders")
data class Sender(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isExcluded: Boolean = false,
    val totalSpent: Double = 0.0,
    val transactionCount: Int = 0,
    val lastTransactionDate: Date? = null
)