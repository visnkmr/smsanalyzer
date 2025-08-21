package com.smsanalytics.smstransactionanalyzer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: TransactionType, // DEBIT or CREDIT
    val description: String,
    val date: Date,
    val smsBody: String,
    val sender: String
)

enum class TransactionType {
    DEBIT, CREDIT
}

data class DailySummary(
    val date: Date,
    val totalSpent: Double,
    val transactionCount: Int,
    val transactions: List<Transaction>
)

data class MonthlySummary(
    val month: String,
    val totalSpent: Double,
    val transactionCount: Int,
    val dailySummaries: List<DailySummary>
)
