package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.*
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getTransactionsBetweenDates(startDate: Long, endDate: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY date DESC")
    fun getTransactionsByType(type: com.smsanalytics.smstransactionanalyzer.model.TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE sender = :sender ORDER BY date DESC")
    fun getTransactionsBySender(sender: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'DEBIT'")
    suspend fun getTotalDebitAmount(): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'CREDIT'")
    suspend fun getTotalCreditAmount(): Double?
}
