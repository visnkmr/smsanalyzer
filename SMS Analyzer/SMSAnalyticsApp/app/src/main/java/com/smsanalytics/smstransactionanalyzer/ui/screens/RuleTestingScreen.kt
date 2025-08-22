package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smsanalytics.smstransactionanalyzer.database.SMSDatabase
import com.smsanalytics.smstransactionanalyzer.model.*
import com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleTestingScreen(
    navController: NavController,
    database: SMSDatabase,
    transactions: List<Transaction>
) {
    val coroutineScope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<SMSAnalysisCache>>(emptyList()) }
    var rules by remember { mutableStateOf<List<CategoryRule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRule by remember { mutableStateOf<CategoryRule?>(null) }
    var testResults by remember { mutableStateOf<List<CategoryMatch>>(emptyList()) }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                database.smsAnalysisCacheDao().getAllCachedMessages().collect { messageList ->
                    messages = messageList.take(50) // Limit for testing
                }
            } catch (e: Exception) {
                // Handle error
            }
        }

        coroutineScope.launch {
            try {
                database.categoryRuleDao().getAllRules().collect { ruleList ->
                    rules = ruleList
                    isLoading = false
                }
            } catch (e: Exception) {
                isLoading = false
            }
        }
    }

    // Test selected rule when it changes
    LaunchedEffect(selectedRule, messages) {
        if (selectedRule != null) {
            testResults = testRuleOnMessages(selectedRule!!, messages)
        } else {
            testResults = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧪 Rule Testing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { navController.navigateUp() }) {
                Text("❌", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Rule Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Rule to Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (rules.isEmpty()) {
                        Text(
                            text = "No rules available. Create rules in Category Rules first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedRule?.name ?: "Select a rule",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Clear Selection") },
                                    onClick = {
                                        selectedRule = null
                                        expanded = false
                                    }
                                )
                                rules.forEach { rule ->
                                    DropdownMenuItem(
                                        text = { Text(rule.name) },
                                        onClick = {
                                            selectedRule = rule
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedRule != null) {
                // Test Results
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Test Results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${testResults.size} matches",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (testResults.isEmpty()) {
                            Text(
                                text = "No messages match this rule",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Messages that would be categorized:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            LazyColumn(
                                modifier = Modifier.height(400.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(testResults) { match ->
                                    TestResultCard(match = match)
                                }
                            }
                        }
                    }
                }
            } else {
                // Instructions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "How to Test Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("1. Select a rule from the dropdown above")
                        Text("2. The system will test the rule against recent messages")
                        Text("3. View matching messages with detected amounts")
                        Text("4. Each match shows the confidence level and category")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "This helps you verify that your rules work correctly before applying them to all transactions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestResultCard(match: CategoryMatch) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.transaction.description.take(50) + if (match.transaction.description.length > 50) "..." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                    Text(
                        text = "From: ${match.transaction.sender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(
                            text = "₹${String.format("%.2f", match.transaction.amount)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${String.format("%.0f", match.confidence * 100)}% match",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Category: ${match.rule.category}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Rule: ${match.rule.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun testRuleOnMessages(rule: CategoryRule, messages: List<SMSAnalysisCache>): List<CategoryMatch> {
    val matches = mutableListOf<CategoryMatch>()

    for (message in messages) {
        if (message.hasTransaction && message.transactionAmount != null) {
            val transaction = Transaction(
                amount = message.transactionAmount ?: 0.0,
                type = message.transactionType ?: TransactionType.DEBIT,
                description = message.messageBody,
                date = java.util.Date(message.timestamp),
                smsBody = message.messageBody,
                sender = message.sender
            )

            val confidence = calculateRuleMatchConfidence(rule, transaction)
            if (confidence > 0.1) { // Only include matches with >10% confidence
                matches.add(CategoryMatch(rule, transaction, confidence))
            }
        }
    }

    return matches.sortedByDescending { it.confidence }
}

private fun calculateRuleMatchConfidence(rule: CategoryRule, transaction: Transaction): Double {
    var confidence = 0.0
    var factors = 0

    // Keyword matching
    val description = transaction.description.lowercase()
    val keywordMatches = rule.keywords.count { keyword ->
        description.contains(keyword.lowercase())
    }
    if (rule.keywords.isNotEmpty()) {
        confidence += keywordMatches.toDouble() / rule.keywords.size
        factors++
    }

    // Sender pattern matching
    val sender = transaction.sender.lowercase()
    val senderMatches = rule.senderPatterns.count { pattern ->
        try {
            Regex(pattern.lowercase()).containsMatchIn(sender)
        } catch (e: Exception) {
            sender.contains(pattern.lowercase())
        }
    }
    if (rule.senderPatterns.isNotEmpty()) {
        confidence += senderMatches.toDouble() / rule.senderPatterns.size
        factors++
    }

    // Amount range matching
    if (rule.amountRangeMin != null || rule.amountRangeMax != null) {
        var amountMatch = false
        if (rule.amountRangeMin != null && rule.amountRangeMax != null) {
            amountMatch = transaction.amount in rule.amountRangeMin..rule.amountRangeMax
        } else if (rule.amountRangeMin != null) {
            amountMatch = transaction.amount >= rule.amountRangeMin
        } else if (rule.amountRangeMax != null) {
            amountMatch = transaction.amount <= rule.amountRangeMax
        }
        if (amountMatch) confidence += 1.0
        factors++
    }

    return if (factors > 0) confidence / factors else 0.0
}