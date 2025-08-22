package com.smsanalytics.smstransactionanalyzer.model

import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.DailySummary

data class YearlySummary(
    val year: String,
    val totalSpent: Double,
    val monthlySummaries: List<MonthlySummary>,
    val transactionCount: Int
)