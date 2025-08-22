package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smsanalytics.smstransactionanalyzer.model.DailySummary
import com.smsanalytics.smstransactionanalyzer.model.MonthlySummary
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.YearlySummary
import com.smsanalytics.smstransactionanalyzer.export.ExportFormat
import com.smsanalytics.smstransactionanalyzer.export.CompressionType
import java.text.SimpleDateFormat
import java.util.*

enum class SortOption {
    AMOUNT_DESC, // Most spend
    DATE_ASC,    // Oldest first
    DATE_DESC    // Newest first (default)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    // State
    transactions: List<Transaction>,
    dailySummaries: List<DailySummary>,
    monthlySummaries: List<MonthlySummary>,
    yearlySummaries: List<YearlySummary>,
    hasPermission: Boolean,
    isLoading: Boolean,
    hasUnsavedChanges: Boolean,
    // Callbacks
    onRequestPermission: () -> Unit,
    onSyncChanges: () -> Unit,
    onExcludeTransaction: (Transaction) -> Unit,
    onExportData: (ExportFormat, CompressionType) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SMS Analytics App",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sync button - only show when there are changes
                    if (hasUnsavedChanges) {
                        IconButton(
                            onClick = onSyncChanges,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("🔄", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    // Navigation dropdown menu
                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = true }) {
                        Text("☰", style = MaterialTheme.typography.titleMedium)
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("🚫 Excluded Messages") },
                            onClick = {
                                navController.navigate("excluded_messages")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("💰 Credit Summaries") },
                            onClick = {
                                navController.navigate("credit_summaries")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("💬 Message Browser") },
                            onClick = {
                                navController.navigate("message_browser")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⚙️ Category Rules") },
                            onClick = {
                                navController.navigate("category_rules")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🏪 Vendor Management") },
                            onClick = {
                                navController.navigate("vendor_management")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📱 Transaction SMS View") },
                            onClick = {
                                navController.navigate("transaction_sms_view")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("👥 Vendor Groups") },
                            onClick = {
                                navController.navigate("vendor_group_management")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📊 Group Spending") },
                            onClick = {
                                navController.navigate("group_spending_overview")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📤 Sender Management") },
                            onClick = {
                                navController.navigate("sender_management")
                                expanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⚙️ Settings") },
                            onClick = {
                                navController.navigate("settings")
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Main content sections
        if (!hasPermission) {
            item {
                PermissionRequestCard(onRequestPermission)
            }
        } else if (isLoading) {
            item {
                LoadingCard()
            }
        } else {
            item {
                SpendingOverviewCard(transactions)
            }
            item {
                ExportOptionsCard(onExportData)
            }
            item {
                YearlySpendingList(yearlySummaries)
            }
            item {
                MonthlySpendingList(monthlySummaries)
            }
            item {
                DailySpendingList(dailySummaries, onExcludeTransaction)
            }
        }
    }
}

@Composable
fun PermissionRequestCard(onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SMS Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This app needs SMS permission to analyze your transactions",
                style = MaterialTheme.typography.bodyMedium
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Analyzing your SMS messages...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpendingOverviewCard(transactions: List<Transaction>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Total Spending",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val totalSpending = transactions.sumOf { it.amount }
            val avgDaily = if (transactions.isNotEmpty()) {
                val days = transactions.groupBy {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.date)
                }.size
                if (days > 0) totalSpending / days else 0.0
            } else 0.0

            val avgMonthly = if (transactions.isNotEmpty()) {
                val months = transactions.groupBy {
                    val cal = Calendar.getInstance().apply { time = it.date }
                    "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
                }.size
                if (months > 0) totalSpending / months else 0.0
            } else 0.0

            Text("Total: ₹${String.format("%.2f", totalSpending)}")
            Text("Daily Average: ₹${String.format("%.2f", avgDaily)}")
            Text("Monthly Average: ₹${String.format("%.2f", avgMonthly)}")
            Text("Transactions Found: ${transactions.size}")
        }
    }
}

@Composable
fun ExportOptionsCard(onExportData: (ExportFormat, CompressionType) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Export Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onExportData(ExportFormat.CSV, CompressionType.NONE) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export CSV")
                }

                Button(
                    onClick = { onExportData(ExportFormat.JSON, CompressionType.GZIP) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export JSON")
                }

                Button(
                    onClick = { onExportData(ExportFormat.PROTOBUF, CompressionType.GZIP) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export Protobuf")
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Spending",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (dailySummaries.isEmpty()) {
                Text("No transactions found")
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monthly Spending",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (monthlySummaries.isEmpty()) {
                Text("No transactions found")
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yearly Spending",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (yearlySummaries.isEmpty()) {
                Text("No yearly data available")
            } else {
                LazyColumn(
                    modifier = Modifier.height(300.dp)
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
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
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
            Text(
                "Transactions (${summary.transactions.size}):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
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

            // Show monthly summary statistics
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

            // Show yearly summary statistics
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
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(transaction.date),
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
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    var showTransactions by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { showTransactions = !showTransactions },
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