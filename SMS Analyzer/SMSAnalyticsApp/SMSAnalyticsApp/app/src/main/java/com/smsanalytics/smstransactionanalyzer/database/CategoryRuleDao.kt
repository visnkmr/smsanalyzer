package com.smsanalytics.smstransactionanalyzer.database

import androidx.room.*
import com.smsanalytics.smstransactionanalyzer.model.CategoryRule
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {

    @Query("SELECT * FROM category_rules ORDER BY priority DESC, name ASC")
    fun getAllRules(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules WHERE isActive = 1 ORDER BY priority DESC, name ASC")
    fun getActiveRules(): Flow<List<CategoryRule>>

    @Query("SELECT * FROM category_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): CategoryRule?

    @Query("SELECT * FROM category_rules WHERE category = :category")
    suspend fun getRulesByCategory(category: String): List<CategoryRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CategoryRule): Long

    @Update
    suspend fun updateRule(rule: CategoryRule)

    @Delete
    suspend fun deleteRule(rule: CategoryRule)

    @Query("DELETE FROM category_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("UPDATE category_rules SET isActive = :isActive WHERE id = :id")
    suspend fun setRuleActive(id: Long, isActive: Boolean)

    @Query("SELECT COUNT(*) FROM category_rules WHERE isActive = 1")
    suspend fun getActiveRulesCount(): Int

    @Query("SELECT DISTINCT category FROM category_rules ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>
}
