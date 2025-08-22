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
import com.smsanalytics.smstransactionanalyzer.model.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataInspectionScreen(navController: NavController) {
    var appData by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load comprehensive app data
    LaunchedEffect(Unit) {
        try {
            // This would normally load data from the database
            // For now, we'll show placeholder data
            appData = mapOf(
                "Total Transactions" to 0,
                "Total Spending" to 0.0,
                "Daily Average" to 0.0,
                "Monthly Average" to 0.0,
                "Daily Summaries Count" to 0,
                "Monthly Summaries Count" to 0,
                "Yearly Summaries Count" to 0,
                "Cached Transactions" to 0,
                "Excluded Messages" to 0,
                "Vendors" to 0,
                "Senders" to 0,
                "Vendor Groups" to 0,
                "Debit Transactions" to 0,
                "Credit Transactions" to 0,
                "Total Debit Amount" to 0.0,
                "Total Credit Amount" to 0.0,
                "Date Range" to "No transactions",
                "Top 5 Days by Spending" to emptyList<String>(),
                "Top 5 Months by Spending" to emptyList<String>()
            )
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Data Inspection",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { navController.navigate("settings") }) {
                Text("←")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                text = "Loading app data...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Overview section
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "📊 Data Overview",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Total Transactions: ${appData["Total Transactions"]}")
                            Text("Total Spending: ₹${String.format("%.2f", appData["Total Spending"] as? Double ?: 0.0)}")
                            Text("Date Range: ${appData["Date Range"]}")
                        }
                    }
                }

                // Transaction statistics
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "💰 Transaction Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Debit Transactions: ${appData["Debit Transactions"]} (₹${String.format("%.2f", appData["Total Debit Amount"] as? Double ?: 0.0)})")
                            Text("Credit Transactions: ${appData["Credit Transactions"]} (₹${String.format("%.2f", appData["Total Credit Amount"] as? Double ?: 0.0)})")
                            Text("Daily Average: ₹${String.format("%.2f", appData["Daily Average"] as? Double ?: 0.0)}")
                            Text("Monthly Average: ₹${String.format("%.2f", appData["Monthly Average"] as? Double ?: 0.0)}")
                        }
                    }
                }

                // Database statistics
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🗄️ Database Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Cached Transactions: ${appData["Cached Transactions"]}")
                            Text("Excluded Messages: ${appData["Excluded Messages"]}")
                            Text("Vendors: ${appData["Vendors"]}")
                            Text("Senders: ${appData["Senders"]}")
                            Text("Vendor Groups: ${appData["Vendor Groups"]}")
                        }
                    }
                }

                // Summary statistics
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "📈 Summary Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Daily Summaries: ${appData["Daily Summaries Count"]}")
                            Text("Monthly Summaries: ${appData["Monthly Summaries Count"]}")
                            Text("Yearly Summaries: ${appData["Yearly Summaries Count"]}")
                        }
                    }
                }

                // Top spending periods
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🏆 Top Spending Periods",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Top 5 Days:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            (appData["Top 5 Days by Spending"] as? List<String>)?.forEach { day ->
                                Text("• $day", style = MaterialTheme.typography.bodySmall)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Top 5 Months:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            (appData["Top 5 Months by Spending"] as? List<String>)?.forEach { month ->
                                Text("• $month", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Raw data export option
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "📤 Export Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    // Export comprehensive data
                                    // exportData(ExportFormat.JSON, CompressionType.NONE)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export All Data (JSON)")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    // Export statistics only
                                    // exportData(ExportFormat.CSV, CompressionType.NONE)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export Statistics (CSV)")
                            }
                        }
                    }
                }
            }
        }
    }
}