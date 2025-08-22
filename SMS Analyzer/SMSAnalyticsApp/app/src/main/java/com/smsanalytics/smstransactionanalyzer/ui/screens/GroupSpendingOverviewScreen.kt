package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.smsanalytics.smstransactionanalyzer.model.Vendor
import com.smsanalytics.smstransactionanalyzer.model.VendorGroup
import com.smsanalytics.smstransactionanalyzer.model.VendorGroupMember
import com.smsanalytics.smstransactionanalyzer.model.VendorGroupWithVendors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSpendingOverviewScreen(
    navController: NavController,
    database: SMSDatabase
) {
    val coroutineScope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<VendorGroup>>(emptyList()) }
    var vendors by remember { mutableStateOf<List<Vendor>>(emptyList()) }
    var groupMembers by remember { mutableStateOf<List<VendorGroupMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Calculate group statistics
    val groupsWithVendors = remember(groups, vendors, groupMembers) {
        groups.map { group ->
            val groupVendorIds = groupMembers.filter { it.groupId == group.id }.map { it.vendorId }
            val groupVendors = vendors.filter { it.id in groupVendorIds }
            val totalSpent = groupVendors.sumOf { it.totalSpent }
            VendorGroupWithVendors(
                group = group,
                vendors = groupVendors,
                totalSpent = totalSpent
            )
        }.sortedByDescending { it.totalSpent } // Sort by total spending
    }

    val totalGroupSpending = groupsWithVendors.sumOf { it.totalSpent }
    val averageGroupSpending = if (groupsWithVendors.isNotEmpty()) totalGroupSpending / groupsWithVendors.size else 0.0

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                database.vendorGroupDao().getAllVendorGroups().collect { groupList ->
                    groups = groupList
                }
            } catch (e: Exception) {
                // Handle error
            }
        }

        coroutineScope.launch {
            try {
                database.vendorDao().getAllVendors().collect { vendorList ->
                    vendors = vendorList
                }
            } catch (e: Exception) {
                // Handle error
            }
        }

        coroutineScope.launch {
            try {
                database.vendorGroupDao().getAllGroupMembers().collect { memberList ->
                    groupMembers = memberList
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
                text = "📊 Group Spending Overview",
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
        } else if (groupsWithVendors.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No vendor groups found",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create groups in vendor management to see spending overview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.navigate("vendor_group_management") }) {
                        Text("Manage Groups")
                    }
                }
            }
        } else {
            // Overall Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Total Groups",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${groupsWithVendors.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Total Spending",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${String.format("%.0f", totalGroupSpending)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Avg per Group",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${String.format("%.0f", averageGroupSpending)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Top Spending Groups
            if (groupsWithVendors.isNotEmpty()) {
                Text(
                    text = "Group Spending Breakdown",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupsWithVendors) { groupWithVendors ->
                        GroupSpendingCard(
                            groupWithVendors = groupWithVendors,
                            totalSpending = totalGroupSpending,
                            onClick = {
                                // Navigate to group details
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSpendingCard(
    groupWithVendors: VendorGroupWithVendors,
    totalSpending: Double,
    onClick: () -> Unit
) {
    val percentage = if (totalSpending > 0) (groupWithVendors.totalSpent / totalSpending) * 100 else 0.0

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(android.graphics.Color.parseColor(groupWithVendors.group.color))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = groupWithVendors.group.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${groupWithVendors.vendors.size} vendors",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${String.format("%.0f", groupWithVendors.totalSpent)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${String.format("%.1f", percentage)}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Progress bar for percentage
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = percentage.toFloat() / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = Color(android.graphics.Color.parseColor(groupWithVendors.group.color))
            )
        }
    }
}