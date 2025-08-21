package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.*
import com.smsanalytics.smstransactionanalyzer.model.Sender
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderDao {

    @Query("SELECT * FROM senders ORDER BY name ASC")
    fun getAllSenders(): Flow<List<Sender>>

    @Query("SELECT * FROM senders WHERE isExcluded = 0 ORDER BY name ASC")
    fun getActiveSenders(): Flow<List<Sender>>

    @Query("SELECT * FROM senders WHERE isExcluded = 1 ORDER BY name ASC")
    fun getExcludedSenders(): Flow<List<Sender>>

    @Query("SELECT * FROM senders WHERE name LIKE '%' || :search || '%' ORDER BY name ASC")
    fun searchSenders(search: String): Flow<List<Sender>>

    @Query("SELECT * FROM senders WHERE id = :id")
    suspend fun getSenderById(id: Long): Sender?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSender(sender: Sender): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSenders(senders: List<Sender>)

    @Update
    suspend fun updateSender(sender: Sender)

    @Delete
    suspend fun deleteSender(sender: Sender)

    @Query("UPDATE senders SET isExcluded = :isExcluded WHERE id = :senderId")
    suspend fun updateSenderExclusion(senderId: Long, isExcluded: Boolean)

    @Query("DELETE FROM senders")
    suspend fun deleteAllSenders()
}