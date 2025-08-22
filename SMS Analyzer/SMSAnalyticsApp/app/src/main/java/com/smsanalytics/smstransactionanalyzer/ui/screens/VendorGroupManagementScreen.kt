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
fun VendorGroupManagementScreen(
    navController: NavController,
    database: SMSDatabase
) {
    val coroutineScope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<VendorGroup>>(emptyList()) }
    var vendors by remember { mutableStateOf<List<Vendor>>(emptyList()) }
    var groupMembers by remember { mutableStateOf<List<VendorGroupMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

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
        }
    }

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
                text = "👥 Vendor Groups",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showCreateGroupDialog = true }) {
                    Text("➕", style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = { navController.navigateUp() }) {
                    Text("❌", style = MaterialTheme.typography.titleMedium)
                }
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
                        text = "No vendor groups yet",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create groups to organize your vendors",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showCreateGroupDialog = true }) {
                        Text("Create First Group")
                    }
                }
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
                    Text("Total Groups: ${groupsWithVendors.size}")
                    Text("Total Vendors in Groups: ${groupsWithVendors.sumOf { it.vendors.size }}")
                    Text("Total Group Spending: ₹${String.format("%.2f", groupsWithVendors.sumOf { it.totalSpent })}")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Groups List
            Text(
                text = "Vendor Groups",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groupsWithVendors) { groupWithVendors ->
                    VendorGroupCard(
                        groupWithVendors = groupWithVendors,
                        onClick = {
                            // Navigate to group details or edit
                        }
                    )
                }
            }
        }
    }

    // Create Group Dialog
    if (showCreateGroupDialog) {
        CreateVendorGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreateGroup = { name, color ->
                coroutineScope.launch {
                    val newGroup = VendorGroup(name = name, color = color)
                    val groupId = database.vendorGroupDao().insertVendorGroup(newGroup)
                    showCreateGroupDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorGroupCard(
    groupWithVendors: VendorGroupWithVendors,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "●",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(android.graphics.Color.parseColor(groupWithVendors.group.color))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = groupWithVendors.group.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${groupWithVendors.vendors.size} vendors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "₹${String.format("%.2f", groupWithVendors.totalSpent)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (groupWithVendors.vendors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Vendors: ${groupWithVendors.vendors.joinToString(", ") { it.name }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVendorGroupDialog(
    onDismiss: () -> Unit,
    onCreateGroup: (String, String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FF5722") }

    val colors = listOf(
        "#FF5722", "#2196F3", "#4CAF50", "#FF9800", "#9C27B0",
        "#795548", "#607D8B", "#E91E63", "#3F51B5", "#009688"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Group") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Choose Color",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { selectedColor = color }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedColor == color) "●" else "○",
                                color = Color(android.graphics.Color.parseColor(color))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (groupName.isNotBlank()) {
                        onCreateGroup(groupName, selectedColor)
                    }
                },
                enabled = groupName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}