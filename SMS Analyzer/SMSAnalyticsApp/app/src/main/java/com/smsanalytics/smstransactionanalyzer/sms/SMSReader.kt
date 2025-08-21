package com.smsanalytics.smstransactionanalyzer.sms

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.parser.SMSParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SMSReader(private val context: Context) {

    private val parser = SMSParser()
    private val smsUri = Telephony.Sms.CONTENT_URI

    companion object {
        const val PERMISSION_READ_SMS = android.Manifest.permission.READ_SMS
    }

    fun hasSMSPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            PERMISSION_READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun readTransactionSMS(onProgress: (progress: Int, message: String) -> Unit = { _, _ -> }): List<Transaction> = withContext(Dispatchers.IO) {
        if (!hasSMSPermission()) {
            onProgress(0, "SMS permission not granted")
            return@withContext emptyList()
        }

        onProgress(5, "Initializing SMS reader...")

        val transactions = mutableListOf<Transaction>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE
        )

        val selection = "${Telephony.Sms.BODY} LIKE ? OR " +
                       "${Telephony.Sms.BODY} LIKE ? OR " +
                       "${Telephony.Sms.BODY} LIKE ? OR " +
                       "${Telephony.Sms.BODY} LIKE ?"

        val selectionArgs = arrayOf("%rs%", "%₹%", "%inr%", "%debited%")

        try {
            onProgress(10, "Querying SMS database...")

            val cursor = contentResolver.query(
                smsUri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )

            cursor?.use {
                val totalMessages = it.count
                var processedMessages = 0

            onProgress(20, "Found $totalMessages potential transaction messages")

                while (it.moveToNext()) {
                    try {
                        val smsBody = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                        val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                        val timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                        processedMessages++
                        val progress = 20 + (processedMessages * 70 / totalMessages)

                        // Show progress updates less frequently to prevent UI overlapping
                        if (processedMessages % 25 == 0 || processedMessages <= 5 || processedMessages == totalMessages) {
                            val progressMessage = when {
                                processedMessages == totalMessages -> {
                                    "Processed all $totalMessages messages, found ${transactions.size} transactions"
                                }
                                processedMessages >= totalMessages * 0.75 -> {
                                    "Almost done... ($processedMessages / $totalMessages)"
                                }
                                processedMessages >= totalMessages * 0.5 -> {
                                    "Processing messages... ($processedMessages / $totalMessages)"
                                }
                                processedMessages >= totalMessages * 0.25 -> {
                                    "Analyzing SMS messages... ($processedMessages / $totalMessages)"
                                }
                                else -> {
                                    "Starting analysis... ($processedMessages / $totalMessages)"
                                }
                            }
                            onProgress(progress, progressMessage)
                        }

                        // Check if this looks like a transaction SMS
                        if (parser.isTransactionSMS(smsBody)) {
                            val transaction = parser.parseTransaction(smsBody, sender, timestamp)
                            transaction?.let { transactions.add(it) }
                        }

                        // Small delay to show progress for demo purposes
                        kotlinx.coroutines.delay(1)
                    } catch (e: Exception) {
                        // Skip malformed SMS
                        continue
                    }
                }
            }

            onProgress(90, "Finalizing transaction analysis...")
            kotlinx.coroutines.delay(100) // Brief pause for UX

            onProgress(100, "Analysis complete! Found ${transactions.size} transactions")

        } catch (e: Exception) {
            onProgress(0, "Error reading SMS: ${e.message}")
            return@withContext emptyList()
        }

        return@withContext transactions
    }

    suspend fun readAllSMS(): List<SMSMessage> = withContext(Dispatchers.IO) {
        if (!hasSMSPermission()) {
            return@withContext emptyList()
        }

        val messages = mutableListOf<SMSMessage>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        try {
            contentResolver.query(
                smsUri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val smsBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                        val sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                        val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))

                        messages.add(
                            SMSMessage(
                                id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                                body = smsBody,
                                sender = sender,
                                timestamp = timestamp,
                                type = type
                            )
                        )
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        return@withContext messages
    }

    data class SMSMessage(
        val id: Long,
        val body: String,
        val sender: String,
        val timestamp: Long,
        val type: Int
    )
}
