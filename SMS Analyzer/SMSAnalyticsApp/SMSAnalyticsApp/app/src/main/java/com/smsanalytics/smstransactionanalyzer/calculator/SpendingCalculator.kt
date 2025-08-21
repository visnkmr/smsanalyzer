package com.smsanalytics.smstransactionanalyzer.calculator

import com.smsanalytics.smstransactionanalyzer.model.DailySummary
import com.smsanalytics.smstransactionanalyzer.model.MonthlySummary
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.TransactionType
import java.text.SimpleDateFormat
import java.util.*

class SpendingCalculator {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    fun calculateDailySpending(transactions: List<Transaction>): List<DailySummary> {
        // Group transactions by date
        val transactionsByDate = transactions.groupBy { transaction ->
            dateFormat.format(transaction.date)
        }

        return transactionsByDate.map { (dateString, dayTransactions) ->
            val totalSpent = dayTransactions
                .filter { it.type == TransactionType.DEBIT }
                .sumOf { it.amount }

            DailySummary(
                date = dateFormat.parse(dateString) ?: Date(),
                totalSpent = totalSpent,
                transactionCount = dayTransactions.size,
                transactions = dayTransactions
            )
        }.sortedByDescending { it.date }
    }

    fun calculateMonthlySpending(transactions: List<Transaction>): List<MonthlySummary> {
        // First get daily summaries
        val dailySummaries = calculateDailySpending(transactions)

        // Group daily summaries by month
        val dailyByMonth = dailySummaries.groupBy { dailySummary ->
            monthFormat.format(dailySummary.date)
        }

        return dailyByMonth.map { (monthString, dailyList) ->
            val totalSpent = dailyList.sumOf { it.totalSpent }
            val transactionCount = dailyList.sumOf { it.transactionCount }

            MonthlySummary(
                month = monthString,
                totalSpent = totalSpent,
                transactionCount = transactionCount,
                dailySummaries = dailyList
            )
        }.sortedByDescending { it.month }
    }

    fun calculateTotalSpending(transactions: List<Transaction>): Double {
        return transactions
            .filter { it.type == TransactionType.DEBIT }
            .sumOf { it.amount }
    }

    fun getTopSpendingDays(dailySummaries: List<DailySummary>, limit: Int = 5): List<DailySummary> {
        return dailySummaries
            .sortedByDescending { it.totalSpent }
            .take(limit)
    }

    fun getTopSpendingMonths(monthlySummaries: List<MonthlySummary>, limit: Int = 5): List<MonthlySummary> {
        return monthlySummaries
            .sortedByDescending { it.totalSpent }
            .take(limit)
    }

    fun getAverageDailySpending(transactions: List<Transaction>): Double {
        if (transactions.isEmpty()) return 0.0

        val dailySummaries = calculateDailySpending(transactions)
        if (dailySummaries.isEmpty()) return 0.0

        val totalSpent = dailySummaries.sumOf { it.totalSpent }
        return totalSpent / dailySummaries.size
    }

    fun getAverageMonthlySpending(transactions: List<Transaction>): Double {
        if (transactions.isEmpty()) return 0.0

        val monthlySummaries = calculateMonthlySpending(transactions)
        if (monthlySummaries.isEmpty()) return 0.0

        val totalSpent = monthlySummaries.sumOf { it.totalSpent }
        return totalSpent / monthlySummaries.size
    }

    fun getSpendingByCategory(transactions: List<Transaction>): Map<String, Double> {
        return transactions
            .filter { it.type == TransactionType.DEBIT }
            .groupBy { extractCategory(it.description) }
            .mapValues { (_, categoryTransactions) ->
                categoryTransactions.sumOf { it.amount }
            }
            .toList()
            .sortedByDescending { it.second }
            .toMap()
    }

    private fun extractCategory(description: String): String {
        val lowerDesc = description.lowercase()

        return when {
            lowerDesc.contains("atm") || lowerDesc.contains("withdrawal") -> "ATM Withdrawal"
            lowerDesc.contains("shopping") || lowerDesc.contains("mall") || lowerDesc.contains("store") -> "Shopping"
            lowerDesc.contains("food") || lowerDesc.contains("restaurant") || lowerDesc.contains("cafe") -> "Food & Dining"
            lowerDesc.contains("fuel") || lowerDesc.contains("petrol") || lowerDesc.contains("gas") -> "Fuel"
            lowerDesc.contains("online") || lowerDesc.contains("e-commerce") || lowerDesc.contains("amazon") -> "Online Shopping"
            lowerDesc.contains("utility") || lowerDesc.contains("electricity") || lowerDesc.contains("water") -> "Utilities"
            lowerDesc.contains("transfer") || lowerDesc.contains("upi") || lowerDesc.contains("paytm") -> "Money Transfer"
            lowerDesc.contains("grocery") || lowerDesc.contains("supermarket") -> "Groceries"
            lowerDesc.contains("entertainment") || lowerDesc.contains("movie") || lowerDesc.contains("game") -> "Entertainment"
            lowerDesc.contains("medical") || lowerDesc.contains("pharmacy") || lowerDesc.contains("hospital") -> "Medical"
            else -> "Other"
        }
    }

    fun getDateRange(transactions: List<Transaction>): Pair<Date, Date>? {
        if (transactions.isEmpty()) return null

        val sortedTransactions = transactions.sortedBy { it.date }
        return Pair(sortedTransactions.first().date, sortedTransactions.last().date)
    }
}
