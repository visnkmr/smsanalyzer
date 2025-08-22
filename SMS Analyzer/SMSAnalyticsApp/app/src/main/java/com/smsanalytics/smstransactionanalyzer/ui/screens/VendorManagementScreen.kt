package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smsanalytics.smstransactionanalyzer.database.SMSDatabase
import com.smsanalytics.smstransactionanalyzer.model.Vendor
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.util.VendorExtractor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorManagementScreen(
    navController: NavController,
    transactions: List<Transaction>,
    database: SMSDatabase
) {
    val coroutineScope = rememberCoroutineScope()
    var vendors by remember { mutableStateOf<List<Vendor>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val vendorExtractor = remember { VendorExtractor() }

    // Calculate vendor statistics from transactions
    val vendorStats = remember(transactions) {
        val vendorTransactions = vendorExtractor.extractVendorsFromTransactions(transactions)
        vendorTransactions.map { vendor ->
            Triple(
                vendor.name,
                vendor.transactionCount,
                vendor.totalSpent
            )
        }.sortedByDescending { it.third } // Sort by total amount
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                database.vendorDao().getAllVendors().collect { vendorList ->
                    vendors = vendorList
                    isLoading = false
                }
            } catch (e: Exception) {
                isLoading = false
            }
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
                text = "🏪 Vendor Management",
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
        } else if (vendorStats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No vendors found",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total Vendors: ${vendorStats.size}")
                    Text("Total Transactions: ${vendorStats.sumOf { it.second }}")
                    Text("Total Amount: ₹${String.format("%.2f", vendorStats.sumOf { it.third })}")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate("vendor_group_management") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("👥 Manage Groups")
                }
                OutlinedButton(
                    onClick = { navController.navigate("group_spending_overview") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📊 Group Overview")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vendor List
            Text(
                text = "Vendors Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vendorStats) { (vendorName, transactionCount, totalAmount) ->
                    VendorCard(
                        vendorName = vendorName,
                        transactionCount = transactionCount,
                        totalAmount = totalAmount,
                        onClick = {
                            navController.navigate("vendor_sms_detail/$vendorName")
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorCard(
    vendorName: String,
    transactionCount: Int,
    totalAmount: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vendorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$transactionCount transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${String.format("%.2f", totalAmount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Total Amount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}