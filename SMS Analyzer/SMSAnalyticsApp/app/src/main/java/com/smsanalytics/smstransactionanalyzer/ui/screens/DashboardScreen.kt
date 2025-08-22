package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smsanalytics.smstransactionanalyzer.export.ExportFormat
import com.smsanalytics.smstransactionanalyzer.export.CompressionType
import com.smsanalytics.smstransactionanalyzer.model.DailySummary
import com.smsanalytics.smstransactionanalyzer.model.MonthlySummary
import com.smsanalytics.smstransactionanalyzer.model.YearlySummary
import com.smsanalytics.smstransactionanalyzer.model.Transaction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    transactions: List<Transaction>,
    dailySummaries: List<DailySummary>,
    monthlySummaries: List<MonthlySummary>,
    yearlySummaries: List<YearlySummary>,
    hasPermission: Boolean,
    isLoading: Boolean,
    hasUnsavedChanges: Boolean,
    onRequestPermission: () -> Unit,
    onSyncChanges: () -> Unit,
    onExcludeTransaction: (Transaction) -> Unit,
    onExportData: (ExportFormat, CompressionType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with sync button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📊 Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasUnsavedChanges) {
                    IconButton(onClick = onSyncChanges) {
                        Text("🔄", style = MaterialTheme.typography.titleMedium)
                    }
                }
                IconButton(onClick = { navController.navigate("settings") }) {
                    Text("⚙️", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (!hasPermission) {
            PermissionRequestCard(onRequestPermission)
        } else if (isLoading) {
            LoadingCard()
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Spending Overview
                item {
                    SpendingOverviewCard(transactions)
                }

                // Quick Access Section
                item {
                    QuickAccessCard(navController)
                }

                // Export Options
                item {
                    ExportOptionsCard(onExportData)
                }

                // Daily Spending List
                item {
                    DailySpendingList(dailySummaries, onExcludeTransaction)
                }

                // Monthly Spending List
                item {
                    MonthlySpendingList(monthlySummaries)
                }

                // Yearly Spending List
                item {
                    YearlySpendingList(yearlySummaries)
                }
            }
        }
    }
}

@Composable
fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📱 SMS Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This app needs SMS permission to analyze your transaction messages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun LoadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Analyzing SMS messages...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun SpendingOverviewCard(transactions: List<Transaction>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "💰 Spending Overview",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val totalSpent = transactions.sumOf { it.amount }
            val debitTransactions = transactions.filter { it.type == com.smsanalytics.smstransactionanalyzer.model.TransactionType.DEBIT }
            val creditTransactions = transactions.filter { it.type == com.smsanalytics.smstransactionanalyzer.model.TransactionType.CREDIT }

            Text("Total Transactions: ${transactions.size}")
            Text("Total Debit: ₹${String.format("%.2f", debitTransactions.sumOf { it.amount })}")
            Text("Total Credit: ₹${String.format("%.2f", creditTransactions.sumOf { it.amount })}")
            Text("Net Spending: ₹${String.format("%.2f", totalSpent)}")
        }
    }
}

@Composable
fun QuickAccessCard(navController: NavController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🚀 Quick Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val quickAccessItems = listOf(
                Triple("📱 All Messages", "all_messages", "View all SMS messages"),
                Triple("🏪 Vendor Management", "vendor_management", "Manage and exclude vendors"),
                Triple("📱 Sender Management", "sender_management", "Manage and exclude senders"),
                Triple("⚙️ Category Rules", "category_rules", "Manage transaction categorization rules"),
                Triple("🧪 Test Rules", "rule_testing", "Test categorization rules on messages"),
                Triple("👥 Vendor Groups", "vendor_group_management", "Create and manage vendor groups"),
                Triple("📊 Group Spending", "group_spending_overview", "View spending by vendor groups"),
                Triple("📤 Transaction SMS View", "transaction_sms_view", "View all transaction-related SMS"),
                Triple("🚫 Excluded Messages", "excluded_messages", "View and manage excluded transactions"),
                Triple("💰 Credit Summaries", "credit_summaries", "View all credit transactions and summaries")
            )

            quickAccessItems.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { (title, route, description) ->
                        OutlinedButton(
                            onClick = { navController.navigate(route) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ExportOptionsCard(onExportData: (ExportFormat, CompressionType) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📤 Export Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onExportData(ExportFormat.JSON, CompressionType.NONE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("JSON")
                }
                Button(
                    onClick = { onExportData(ExportFormat.CSV, CompressionType.NONE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CSV")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySpendingList(
    dailySummaries: List<DailySummary>,
    onExcludeTransaction: (Transaction) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📅 Daily Spending",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (dailySummaries.isEmpty()) {
                Text("No daily summaries available", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(dailySummaries) { summary ->
                        DailySpendingItem(summary, onExcludeTransaction)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlySpendingList(monthlySummaries: List<MonthlySummary>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📊 Monthly Spending",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (monthlySummaries.isEmpty()) {
                Text("No monthly summaries available", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(monthlySummaries) { summary ->
                        MonthlySpendingItem(summary)
                    }
                }
            }
        }
    }
}

@Composable
fun YearlySpendingList(yearlySummaries: List<YearlySummary>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📈 Yearly Spending",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (yearlySummaries.isEmpty()) {
                Text("No yearly summaries available", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(yearlySummaries) { summary ->
                        YearlySpendingItem(summary)
                    }
                }
            }
        }
    }
}

@Composable
fun DailySpendingItem(summary: DailySummary, onExcludeTransaction: (Transaction) -> Unit) {
    val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                dateFormat.format(summary.date),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "₹${String.format("%.2f", summary.totalSpent)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showDetails && summary.transactions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.height(150.dp)
            ) {
                items(summary.transactions) { transaction ->
                    TransactionDetailItem(transaction, onExcludeTransaction)
                }
            }
        }

        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun MonthlySpendingItem(summary: MonthlySummary) {
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                summary.month,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "₹${String.format("%.2f", summary.totalSpent)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showDetails && summary.dailySummaries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "📊 ${summary.month} Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total Transactions: ${summary.dailySummaries.sumOf { it.transactionCount }}")
                    Text("Active Days: ${summary.dailySummaries.size}")
                    Text("Average Daily: ₹${String.format("%.2f", summary.totalSpent / summary.dailySummaries.size)}")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "📅 Daily Breakdown:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(summary.dailySummaries) { dailySummary ->
                    DailySummaryDetailItem(dailySummary)
                }
            }
        }

        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun YearlySpendingItem(summary: YearlySummary) {
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                summary.year,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "₹${String.format("%.2f", summary.totalSpent)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showDetails && summary.monthlySummaries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "📊 ${summary.year} Summary",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total Transactions: ${summary.transactionCount}")
                    Text("Total Months with Activity: ${summary.monthlySummaries.size}")
                    Text("Average Monthly: ₹${String.format("%.2f", summary.totalSpent / summary.monthlySummaries.size)}")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "📅 Monthly Breakdown:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier.height(250.dp)
            ) {
                items(summary.monthlySummaries) { monthlySummary ->
                    MonthlySummaryDetailItem(monthlySummary)
                }
            }
        }

        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun MonthlySummaryDetailItem(monthlySummary: MonthlySummary) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    monthlySummary.month,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "₹${String.format("%.2f", monthlySummary.totalSpent)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TransactionDetailItem(transaction: Transaction, onExcludeTransaction: (Transaction) -> Unit) {
    var showExcludeDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(transaction.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "₹${String.format("%.2f", transaction.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == com.smsanalytics.smstransactionanalyzer.model.TransactionType.DEBIT)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )

                    TextButton(
                        onClick = { showExcludeDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Exclude",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showExcludeDialog) {
        AlertDialog(
            onDismissRequest = { showExcludeDialog = false },
            title = { Text("Exclude Transaction") },
            text = {
                Text("Are you sure you want to exclude this transaction from analysis? This will prevent it from being counted in future spending calculations.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onExcludeTransaction(transaction)
                        showExcludeDialog = false
                    }
                ) {
                    Text("Exclude")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExcludeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DailySummaryDetailItem(dailySummary: DailySummary) {
    val dateFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
    var showTransactions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dateFormat.format(dailySummary.date),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "₹${String.format("%.2f", dailySummary.totalSpent)} (${dailySummary.transactionCount} txns)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showTransactions && dailySummary.transactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Transactions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.height(150.dp)
                ) {
                    items(dailySummary.transactions) { transaction ->
                        TransactionDetailItem(transaction, onExcludeTransaction = {})
                    }
                }
            }
        }
    }
}