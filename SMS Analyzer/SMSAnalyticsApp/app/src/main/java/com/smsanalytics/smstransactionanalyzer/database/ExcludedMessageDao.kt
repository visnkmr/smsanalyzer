package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.*
import com.smsanalytics.smstransactionanalyzer.model.ExcludedMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ExcludedMessageDao {

    @Query("SELECT * FROM excluded_messages ORDER BY excludedAt DESC")
    fun getAllExcludedMessages(): Flow<List<ExcludedMessage>>

    @Query("SELECT * FROM excluded_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getExcludedMessageById(messageId: Long): ExcludedMessage?

    @Query("SELECT messageId FROM excluded_messages")
    suspend fun getAllExcludedMessageIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExcludedMessage(excludedMessage: ExcludedMessage): Long

    @Delete
    suspend fun deleteExcludedMessage(excludedMessage: ExcludedMessage)

    @Query("DELETE FROM excluded_messages WHERE messageId = :messageId")
    suspend fun deleteExcludedMessageById(messageId: Long)

    @Query("SELECT COUNT(*) FROM excluded_messages")
    suspend fun getExcludedMessageCount(): Int
}