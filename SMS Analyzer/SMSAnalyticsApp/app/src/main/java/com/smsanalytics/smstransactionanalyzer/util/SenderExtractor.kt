package com.smsanalytics.smstransactionanalyzer.util

import com.smsanalytics.smstransactionanalyzer.model.Sender
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class SenderExtractor {

    suspend fun extractSendersFromTransactions(transactions: List<Transaction>): List<Sender> {
        return withContext(Dispatchers.Default) {
            val senderMap = mutableMapOf<String, Sender>()

            transactions.forEach { transaction ->
                val senderName = transaction.sender ?: return@forEach
                val amount = transaction.amount ?: 0.0

                val existingSender = senderMap[senderName]
                if (existingSender != null) {
                    // Update existing sender with new values
                    val updatedSender = existingSender.copy(
                        totalSpent = existingSender.totalSpent + amount,
                        transactionCount = existingSender.transactionCount + 1,
                        lastTransactionDate = if (transaction.date.after(existingSender.lastTransactionDate ?: Date(0))) {
                            transaction.date
                        } else {
                            existingSender.lastTransactionDate
                        }
                    )
                    senderMap[senderName] = updatedSender
                } else {
                    // Create new sender
                    senderMap[senderName] = Sender(
                        name = senderName,
                        isExcluded = false,
                        totalSpent = amount,
                        transactionCount = 1,
                        lastTransactionDate = transaction.date
                    )
                }
            }

            senderMap.values.toList()
        }
    }
}