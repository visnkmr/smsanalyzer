package com.smsanalytics.smstransactionanalyzer.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.regex.Pattern

@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val keywords: List<String>,
    val senderPatterns: List<String>,
    val amountRangeMin: Double? = null,
    val amountRangeMax: Double? = null,
    val category: String,
    val priority: Int = 0,
    val isActive: Boolean = true,
    val createdDate: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
)

data class CategoryMatch(
    val rule: CategoryRule,
    val transaction: Transaction,
    val confidence: Double
)

enum class BuiltInCategory {
    FOOD_DINING,
    SHOPPING,
    TRANSPORT,
    ENTERTAINMENT,
    BILLS_UTILITIES,
    HEALTHCARE,
    EDUCATION,
    TRAVEL,
    INVESTMENTS,
    INSURANCE,
    SALARY_INCOME,
    TRANSFERS,
    ATM_WITHDRAWAL,
    ONLINE_SERVICES,
    OTHER
}

fun getBuiltInCategories(): List<String> = BuiltInCategory.values().map { it.name.replace("_", " ") }

fun getCategoryIcon(category: String): String = when (category.lowercase()) {
    "food dining" -> "🍽️"
    "shopping" -> "🛍️"
    "transport" -> "🚗"
    "entertainment" -> "🎬"
    "bills utilities" -> "💡"
    "healthcare" -> "🏥"
    "education" -> "📚"
    "travel" -> "✈️"
    "investments" -> "📈"
    "insurance" -> "🛡️"
    "salary income" -> "💰"
    "transfers" -> "↗️"
    "atm withdrawal" -> "🏧"
    "online services" -> "💻"
    else -> "📦"
}
