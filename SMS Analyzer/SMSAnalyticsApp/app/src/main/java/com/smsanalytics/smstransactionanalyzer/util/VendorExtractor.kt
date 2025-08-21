package com.smsanalytics.smstransactionanalyzer.util

import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.Vendor
import java.util.*
import kotlin.collections.ArrayList

class VendorExtractor {

    // Common patterns to extract vendor names from transaction descriptions
    private val vendorPatterns = listOf(
        // Bank transfer patterns
        Regex("from ([A-Za-z\\s&.-]+)"),
        Regex("to ([A-Za-z\\s&.-]+)"),
        Regex("([A-Za-z\\s&.-]+) credited"),
        Regex("credited by ([A-Za-z\\s&.-]+)"),
        Regex("debited by ([A-Za-z\\s&.-]+)"),
        Regex("([A-Za-z\\s&.-]+) debited"),

        // UPI patterns
        Regex("([A-Za-z\\s.-]+)@"),
        Regex("@([A-Za-z\\s.-]+)"),

        // General merchant patterns
        Regex("at ([A-Za-z\\s&.-]+)"),
        Regex("([A-Za-z\\s&.-]+) (?:store|shop|restaurant|hotel)"),
        Regex("([A-Za-z\\s&.-]+) (?:payment|bill|charge)"),

        // Common vendor indicators
        Regex("([A-Za-z\\s&.-]{3,}) (?:₹|\\d)"),
    )

    // Common words to filter out (not vendors)
    private val filterWords = setOf(
        "account", "balance", "payment", "transaction", "transfer", "credit", "debit",
        "bank", "card", "cash", "money", "amount", "total", "fee", "charge", "bill",
        "your", "you", "has", "been", "received", "sent", "paid", "withdrawn", "deposited"
    )

    fun extractVendorsFromTransactions(transactions: List<Transaction>): List<Vendor> {
        val vendorMap = mutableMapOf<String, Vendor>()

        for (transaction in transactions) {
            val extractedNames = extractVendorNames(transaction)

            for (name in extractedNames) {
                val cleanName = cleanVendorName(name)
                if (cleanName.isNotBlank() && cleanName.length >= 2) {
                    val key = cleanName.lowercase()

                    if (vendorMap.containsKey(key)) {
                        // Update existing vendor
                        val existing = vendorMap[key]!!
                        vendorMap[key] = existing.copy(
                            totalSpent = existing.totalSpent + Math.abs(transaction.amount),
                            transactionCount = existing.transactionCount + 1,
                            lastTransactionDate = if (transaction.date.after(existing.lastTransactionDate))
                                transaction.date else existing.lastTransactionDate
                        )
                    } else {
                        // Create new vendor
                        vendorMap[key] = Vendor(
                            name = cleanName,
                            totalSpent = Math.abs(transaction.amount),
                            transactionCount = 1,
                            lastTransactionDate = transaction.date
                        )
                    }
                }
            }
        }

        return vendorMap.values.toList()
    }

    private fun extractVendorNames(transaction: Transaction): List<String> {
        val names = mutableListOf<String>()

        // Try sender first
        if (!transaction.sender.isNullOrBlank()) {
            names.add(transaction.sender!!)
        }

        // Extract from description using patterns
        val description = transaction.smsBody
        for (pattern in vendorPatterns) {
            val matches = pattern.findAll(description)
            for (match in matches) {
                val name = match.groupValues[1].trim()
                if (name.isNotBlank()) {
                    names.add(name)
                }
            }
        }

        // Extract common vendor names using heuristics
        val words = description.split(Regex("[\\s\\-.,/@]+"))
        for (word in words) {
            if (word.length >= 3 && word.all { it.isLetter() } &&
                word.lowercase() !in filterWords) {
                names.add(word)
            }
        }

        return names.distinct()
    }

    private fun cleanVendorName(name: String): String {
        return name
            .trim()
            .split(Regex("[\\s\\-.,/@]+"))
            .filter { word -> word.isNotBlank() && word.length >= 2 }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            .trim()
    }

    fun getVendorSuggestions(): List<String> {
        return listOf(
            "Amazon", "Flipkart", "Myntra", "Zomato", "Swiggy", "Uber", "Ola",
            "IRCTC", "BookMyShow", "Netflix", "Spotify", "Google Play", "App Store",
            "Petrol Pump", "Grocery Store", "Medical Store", "Restaurant", "Hotel",
            "Shopping Mall", "Electronics Store", "Mobile Recharge", "DTH",
            "Electricity Bill", "Water Bill", "Gas Bill", "Internet Bill"
        )
    }
}