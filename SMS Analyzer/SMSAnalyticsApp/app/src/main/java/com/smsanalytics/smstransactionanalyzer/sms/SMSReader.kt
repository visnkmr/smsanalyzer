package com.smsanalytics.smstransactionanalyzer.sms

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SMSReader(private val context: Context) {

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
