package com.smsanalytics.smstransactionanalyzer.parser

import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.TransactionType
import java.util.Date
import java.util.regex.Pattern

class SMSParser {

    // OTP patterns for different banks and services
    private val otpPatterns = listOf(
        // Common OTP patterns (4-8 digits)
        Pattern.compile("(?i)(?:otp|one-time password|verification code|auth code|security code)\\s*[:\\-]?\\s*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([0-9]{4,8})\\s*(?:is your|is the|your)\\s*(?:otp|one-time password|verification code)", Pattern.CASE_INSENSITIVE),

        // Bank-specific OTP patterns
        Pattern.compile("(?i)(?:SBI|HDFC|ICICI|AXIS|PNB|BOB|KOTAK)\\s*(?:otp|verification code)\\s*[:\\-]?\\s*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)OTP\\s*([0-9]{4,8})\\s*(?:for|to)\\s*(?:debit|credit|transaction)", Pattern.CASE_INSENSITIVE),

        // Generic 4-8 digit codes (most common OTP length)
        Pattern.compile("(?i)(?:code|pin|password)\\s*[:\\-]?\\s*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)([0-9]{4,8})\\s*(?:is your|is the)\\s*(?:code|pin)", Pattern.CASE_INSENSITIVE),

        // Transaction verification OTPs
        Pattern.compile("(?i)(?:transaction|payment|transfer)\\s*(?:otp|code)\\s*[:\\-]?\\s*([0-9]{4,8})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)OTP\\s*([0-9]{4,8})\\s*(?:for)\\s*(?:rs|₹|inr)\\s*[0-9,]+(?:\\.[0-9]+)?", Pattern.CASE_INSENSITIVE)
    )

    // Transaction patterns that should only be processed if OTP is present
    private val transactionPatterns = listOf(
        // Common debit patterns
        Pattern.compile("(?i)(?:debited|withdrawn|deducted|charged).*?(?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?).*?(?:debited|withdrawn|deducted|charged)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)payment of (?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE),

        // Common credit patterns
        Pattern.compile("(?i)(?:credited|deposited|received).*?(?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?).*?(?:credited|deposited|received)", Pattern.CASE_INSENSITIVE),

        // Bank specific patterns
        Pattern.compile("(?i)(?:SBI|ICICI|HDFC|AXIS|PNB).*?(?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(?:a/c|account).*?(?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE),

        // Generic amount patterns
        Pattern.compile("(?i)(?:rs|inr|₹)\\s*([0-9,]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE)
    )

    private val debitKeywords = setOf(
        "debited", "withdrawn", "deducted", "charged", "paid", "payment", "spent",
        "purchased", "bought", "transaction", "transfer"
    )

    private val creditKeywords = setOf(
        "credited", "deposited", "received", "refund", "credit", "added"
    )

    fun parseTransaction(smsBody: String, sender: String, timestamp: Long): Transaction? {
        try {
            // Only process if OTP is present (prevents false positives)
            if (!containsOTP(smsBody)) {
                return null
            }

            // Extract amount using patterns
            val amount = extractAmount(smsBody) ?: return null

            // Determine transaction type
            val type = determineTransactionType(smsBody)

            // Extract description
            val description = extractDescription(smsBody)

            return Transaction(
                amount = amount,
                type = type,
                description = description,
                date = Date(timestamp),
                smsBody = smsBody,
                sender = sender
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractAmount(smsBody: String): Double? {
        for (pattern in transactionPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val amountStr = matcher.group(1)?.replace(",", "") ?: continue
                return try {
                    amountStr.toDouble()
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    private fun determineTransactionType(smsBody: String): TransactionType {
        val lowerSms = smsBody.lowercase()

        val debitScore = debitKeywords.count { lowerSms.contains(it) }
        val creditScore = creditKeywords.count { lowerSms.contains(it) }

        return if (debitScore >= creditScore) TransactionType.DEBIT else TransactionType.CREDIT
    }

    private fun extractDescription(smsBody: String): String {
        // Remove common prefixes and clean up
        var description = smsBody
            .replace(Regex("(?i)(?:rs|inr|₹)\\s*[0-9,]+(?:\\.[0-9]+)?"), "")
            .replace(Regex("(?i)(?:debited|credited|withdrawn|deducted|charged|paid|received|deposited)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Limit description length
        return if (description.length > 100) {
            description.substring(0, 97) + "..."
        } else {
            description
        }
    }

    fun isTransactionSMS(smsBody: String): Boolean {
        // Only consider as transaction SMS if it contains OTP (prevents false positives)
        return containsOTP(smsBody) && containsTransactionData(smsBody)
    }

    fun containsOTP(smsBody: String): Boolean {
        return otpPatterns.any { pattern ->
            pattern.matcher(smsBody).find()
        }
    }

    private fun containsTransactionData(smsBody: String): Boolean {
        val lowerSms = smsBody.lowercase()
        return debitKeywords.any { lowerSms.contains(it) } ||
                creditKeywords.any { lowerSms.contains(it) } ||
                lowerSms.contains("rs") || lowerSms.contains("₹") || lowerSms.contains("inr")
    }

    fun extractOTP(smsBody: String): String? {
        for (pattern in otpPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }
}
