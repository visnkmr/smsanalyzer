package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.*
import com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache
import com.smsanalytics.smstransactionanalyzer.model.AnalysisMetadata
import kotlinx.coroutines.flow.Flow

@Dao
interface SMSAnalysisCacheDao {

    @Query("SELECT * FROM sms_analysis_cache ORDER BY timestamp DESC")
    fun getAllCachedMessages(): Flow<List<SMSAnalysisCache>>

    @Query("SELECT * FROM sms_analysis_cache WHERE messageId = :messageId LIMIT 1")
    suspend fun getCachedMessage(messageId: Long): SMSAnalysisCache?

    @Query("SELECT messageId FROM sms_analysis_cache ORDER BY messageId DESC LIMIT 1")
    suspend fun getLastProcessedMessageId(): Long?

    @Query("SELECT MAX(timestamp) FROM sms_analysis_cache")
    suspend fun getLastProcessedTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM sms_analysis_cache WHERE hasTransaction = 1")
    suspend fun getTransactionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMessage(cachedMessage: SMSAnalysisCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMessages(messages: List<SMSAnalysisCache>)

    @Update
    suspend fun updateCachedMessage(cachedMessage: SMSAnalysisCache)

    @Query("DELETE FROM sms_analysis_cache WHERE messageId = :messageId")
    suspend fun deleteCachedMessage(messageId: Long)

    @Query("DELETE FROM sms_analysis_cache")
    suspend fun clearCache()

    // Analysis metadata operations
    @Query("SELECT * FROM analysis_metadata WHERE id = :id LIMIT 1")
    suspend fun getAnalysisMetadata(id: String = "last_analysis"): AnalysisMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysisMetadata(metadata: AnalysisMetadata)

    @Update
    suspend fun updateAnalysisMetadata(metadata: AnalysisMetadata)

    @Query("DELETE FROM analysis_metadata WHERE id = :id")
    suspend fun deleteAnalysisMetadata(id: String = "last_analysis")

    // Get messages newer than a specific timestamp
    @Query("SELECT * FROM sms_analysis_cache WHERE timestamp > :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getMessagesSince(sinceTimestamp: Long): List<SMSAnalysisCache>

    // Get unprocessed message IDs (for incremental processing)
    @Query("SELECT messageId FROM sms_analysis_cache WHERE processedAt < :cutoffTime ORDER BY messageId DESC")
    suspend fun getStaleMessageIds(cutoffTime: Long = System.currentTimeMillis() - 24 * 60 * 60 * 1000): List<Long>

    // Get cached transactions (excluding those marked as excluded)
    @Query("SELECT * FROM sms_analysis_cache WHERE hasTransaction = 1 AND isExcluded = 0 ORDER BY timestamp DESC")
    suspend fun getCachedTransactions(): List<SMSAnalysisCache>

    // Mark a message as excluded
    @Query("UPDATE sms_analysis_cache SET isExcluded = 1 WHERE messageId = :messageId")
    suspend fun markMessageAsExcluded(messageId: Long)

    // Get last analysis metadata
    @Query("SELECT * FROM analysis_metadata ORDER BY lastAnalysisDate DESC LIMIT 1")
    suspend fun getLastAnalysisMetadata(): AnalysisMetadata?
}