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

        // Transaction-focused message filtering (no explicit OTP patterns)
        val selection = "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ?"
        val selectionArgs = arrayOf(
            "%transaction%", "%payment%", "%debit%",
            "%credit%", "%amount%", "%rs%", "%₹%", "%inr%", "%debited%"
        )

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

                        // Check if this looks like a transaction SMS
                        if (parser.isTransactionSMS(smsBody)) {
                            val transaction = parser.parseTransaction(smsBody, sender, timestamp)
                            transaction?.let { transactions.add(it) }
                        }

                        // Show progress updates less frequently to prevent UI overhead
                        if (processedMessages % 100 == 0 || processedMessages <= 10 || processedMessages == totalMessages) {
                            val progress = 20 + (processedMessages * 70 / totalMessages)
                            val progressMessage = when {
                                processedMessages == totalMessages -> {
                                    "Found ${transactions.size} transactions from $totalMessages messages"
                                }
                                processedMessages >= totalMessages * 0.75 -> {
                                    "Processing messages... ($processedMessages / $totalMessages)"
                                }
                                else -> {
                                    "Analyzing SMS messages... ($processedMessages / $totalMessages)"
                                }
                            }
                            onProgress(progress, progressMessage)
                        }
                    } catch (e: Exception) {
                        // Skip malformed SMS
                        continue
                    }
                }
            }

            onProgress(90, "Finalizing transaction analysis...")
            kotlinx.coroutines.delay(100) // Brief pause for UX

            onProgress(100, "Message-based analysis complete! Found ${transactions.size} verified transactions")

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
            Telephony.Sms.TYPE,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.PERSON,
            Telephony.Sms.PROTOCOL,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.SUBJECT,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.CREATOR,
            Telephony.Sms.SEEN,
            Telephony.Sms.REPLY_PATH_PRESENT,
            Telephony.Sms.LOCKED,
            Telephony.Sms.ERROR_CODE,
            Telephony.Sms.SUBSCRIPTION_ID
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
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                        val smsBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                        val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                        val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))

                        // Additional fields with safe reading
                        val threadId = try { cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)) } catch (e: Exception) { 0L }
                        val person = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.PERSON)) } catch (e: Exception) { null }
                        val protocol = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.PROTOCOL)) } catch (e: Exception) { null }
                        val read = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1 } catch (e: Exception) { false }
                        val status = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)) } catch (e: Exception) { -1 }
                        val serviceCenter = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.SERVICE_CENTER)) } catch (e: Exception) { null }
                        val subject = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.SUBJECT)) } catch (e: Exception) { null }
                        val dateSent = try { cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)) } catch (e: Exception) { 0L }
                        val creator = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.CREATOR)) } catch (e: Exception) { null }
                        val seen = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.SEEN)) == 1 } catch (e: Exception) { false }
                        val replyPathPresent = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.REPLY_PATH_PRESENT)) == 1 } catch (e: Exception) { false }
                        val locked = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.LOCKED)) == 1 } catch (e: Exception) { false }
                        val errorCode = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.ERROR_CODE)) } catch (e: Exception) { 0 }
                        val subId = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)) } catch (e: Exception) { 0 }

                        messages.add(
                            SMSMessage(
                                id = id,
                                body = smsBody,
                                sender = address,
                                timestamp = timestamp,
                                type = type,
                                threadId = threadId,
                                address = address,
                                person = person,
                                protocol = protocol,
                                read = read,
                                status = status,
                                serviceCenter = serviceCenter,
                                subject = subject,
                                dateSent = dateSent,
                                creator = creator,
                                seen = seen,
                                priority = 0, // Not available in standard API
                                replyPathPresent = replyPathPresent,
                                locked = locked,
                                errorCode = errorCode,
                                subId = subId
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

    suspend fun readTransactionSMSIncremental(sinceTimestamp: Long, onProgress: (progress: Int, message: String) -> Unit = { _, _ -> }): List<Transaction> = withContext(Dispatchers.IO) {
        if (!hasSMSPermission()) {
            onProgress(0, "SMS permission not granted")
            return@withContext emptyList()
        }

        onProgress(5, "Initializing incremental SMS reader...")

        val transactions = mutableListOf<Transaction>()
        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE
        )

        // Query for messages newer than the sinceTimestamp with transaction-focused filtering
        val selection = "(${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ? OR " +
                        "${Telephony.Sms.BODY} LIKE ?) AND ${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(
            "%transaction%", "%payment%", "%debit%",
            "%credit%", "%amount%", "%verification code%", sinceTimestamp.toString()
        )

        try {
            onProgress(10, "Querying for new SMS messages...")

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

                onProgress(20, "Found $totalMessages new potential messages")

                while (it.moveToNext()) {
                    try {
                        val smsBody = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                        val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                        val timestamp = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))

                        processedMessages++
                        val progress = 20 + (processedMessages * 70 / totalMessages)

                        // Check if this looks like a transaction SMS
                        if (parser.isTransactionSMS(smsBody)) {
                            val transaction = parser.parseTransaction(smsBody, sender, timestamp)
                            transaction?.let { transactions.add(it) }
                        }

                        // Show progress updates less frequently to prevent UI overhead
                        if (processedMessages % 50 == 0 || processedMessages <= 5 || processedMessages == totalMessages) {
                            val progress = 20 + (processedMessages * 70 / totalMessages)
                            val progressMessage = when {
                                processedMessages == totalMessages -> {
                                    "Found ${transactions.size} transactions from $totalMessages new messages"
                                }
                                processedMessages >= totalMessages * 0.75 -> {
                                    "Processing new messages... ($processedMessages / $totalMessages)"
                                }
                                else -> {
                                    "Analyzing new SMS messages... ($processedMessages / $totalMessages)"
                                }
                            }
                            onProgress(progress, progressMessage)
                        }
                    } catch (e: Exception) {
                        // Skip malformed SMS
                        continue
                    }
                }
            }

            onProgress(90, "Finalizing incremental transaction analysis...")
            kotlinx.coroutines.delay(100) // Brief pause for UX

            onProgress(100, "Incremental message analysis complete! Found ${transactions.size} new transactions")

        } catch (e: Exception) {
            onProgress(0, "Error reading incremental SMS: ${e.message}")
            return@withContext emptyList()
        }

        return@withContext transactions
    }

    suspend fun getSMSMessageById(messageId: Long): SMSMessage? = withContext(Dispatchers.IO) {
        if (!hasSMSPermission()) {
            return@withContext null
        }

        val contentResolver: ContentResolver = context.contentResolver

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.PERSON,
            Telephony.Sms.PROTOCOL,
            Telephony.Sms.READ,
            Telephony.Sms.STATUS,
            Telephony.Sms.SERVICE_CENTER,
            Telephony.Sms.SUBJECT,
            Telephony.Sms.DATE_SENT,
            Telephony.Sms.CREATOR,
            Telephony.Sms.SEEN,
            Telephony.Sms.REPLY_PATH_PRESENT,
            Telephony.Sms.LOCKED,
            Telephony.Sms.ERROR_CODE,
            Telephony.Sms.SUBSCRIPTION_ID
        )

        val selection = "${Telephony.Sms._ID} = ?"
        val selectionArgs = arrayOf(messageId.toString())

        try {
            contentResolver.query(
                smsUri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val smsBody = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    val address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE))

                    // Additional fields with safe reading
                    val threadId = try { cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)) } catch (e: Exception) { 0L }
                    val person = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.PERSON)) } catch (e: Exception) { null }
                    val protocol = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.PROTOCOL)) } catch (e: Exception) { null }
                    val read = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1 } catch (e: Exception) { false }
                    val status = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)) } catch (e: Exception) { -1 }
                    val serviceCenter = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.SERVICE_CENTER)) } catch (e: Exception) { null }
                    val subject = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.SUBJECT)) } catch (e: Exception) { null }
                    val dateSent = try { cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)) } catch (e: Exception) { 0L }
                    val creator = try { cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.CREATOR)) } catch (e: Exception) { null }
                    val seen = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.SEEN)) == 1 } catch (e: Exception) { false }
                    val replyPathPresent = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.REPLY_PATH_PRESENT)) == 1 } catch (e: Exception) { false }
                    val locked = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.LOCKED)) == 1 } catch (e: Exception) { false }
                    val errorCode = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.ERROR_CODE)) } catch (e: Exception) { 0 }
                    val subId = try { cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.SUBSCRIPTION_ID)) } catch (e: Exception) { 0 }

                    return@withContext SMSMessage(
                        id = id,
                        body = smsBody,
                        sender = address,
                        timestamp = timestamp,
                        type = type,
                        threadId = threadId,
                        address = address,
                        person = person,
                        protocol = protocol,
                        read = read,
                        status = status,
                        serviceCenter = serviceCenter,
                        subject = subject,
                        dateSent = dateSent,
                        creator = creator,
                        seen = seen,
                        priority = 0, // Not available in standard API
                        replyPathPresent = replyPathPresent,
                        locked = locked,
                        errorCode = errorCode,
                        subId = subId
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext null
        }

        return@withContext null
    }

    data class SMSMessage(
        val id: Long,
        val body: String,
        val sender: String,
        val timestamp: Long,
        val type: Int,
        val threadId: Long = 0,
        val address: String = "",
        val person: String? = null,
        val protocol: Int? = null,
        val read: Boolean = false,
        val status: Int = -1,
        val serviceCenter: String? = null,
        val subject: String? = null,
        val dateSent: Long = 0,
        val creator: String? = null,
        val seen: Boolean = false,
        val priority: Int = 0,
        val replyPathPresent: Boolean = false,
        val locked: Boolean = false,
        val errorCode: Int = 0,
        val subId: Int = 0
    )
}
