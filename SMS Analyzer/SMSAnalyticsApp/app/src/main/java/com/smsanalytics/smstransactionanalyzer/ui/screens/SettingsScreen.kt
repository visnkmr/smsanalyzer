package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var selectedHomeScreen by remember { mutableStateOf("message_browser") }
    var showAppMap by remember { mutableStateOf(false) }
    var showFilteringSettings by remember { mutableStateOf(false) }
    var showCustomFilters by remember { mutableStateOf(false) }
    var newCustomFilter by remember { mutableStateOf("") }

    // Filter patterns state - now just enabled/disabled state
    var enabledPatterns by remember {
        mutableStateOf(setOf(
            "%transaction%", "%payment%", "%debit%", "%credit%", "%amount%",
            "%rs%", "%₹%", "%inr%", "%debited%"
        ))
    }

    // Custom filter patterns
    var customPatterns by remember {
        mutableStateOf(setOf<String>())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { navController.navigate("dashboard") }) {
                    Text("←")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // App Map Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Navigation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Explore all screens and features in the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAppMap = !showAppMap },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (showAppMap) "Hide App Map" else "Show App Map")
                        }

                        Button(
                            onClick = { navController.navigate("data_inspection") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Data Inspection")
                        }
                    }

                    if (showAppMap) {
                        Spacer(modifier = Modifier.height(16.dp))

                        val appPages = listOf(
                            Triple("📊 Dashboard", "dashboard", "Main spending overview with yearly, monthly, and daily summaries"),
                            Triple("💬 Message Browser", "message_browser", "Browse and filter all SMS messages"),
                            Triple("🚫 Excluded Messages", "excluded_messages", "View and manage excluded transactions"),
                            Triple("💰 Credit Summaries", "credit_summaries", "View all credit transactions and summaries"),
                            Triple("⚙️ Category Rules", "category_rules", "Manage transaction categorization rules"),
                            Triple("🏪 Vendor Management", "vendor_management", "Manage and exclude vendors"),
                            Triple("📱 Sender Management", "sender_management", "Manage and exclude senders"),
                            Triple("👥 Vendor Groups", "vendor_group_management", "Create and manage vendor groups"),
                            Triple("📊 Group Spending", "group_spending_overview", "View spending by vendor groups"),
                            Triple("📤 Transaction SMS View", "transaction_sms_view", "View all transaction-related SMS"),
                            Triple("🔍 Data Inspection", "data_inspection", "Inspect all app data and statistics")
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "📍 App Map - All Available Pages",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyColumn(
                                    modifier = Modifier.height(300.dp)
                                ) {
                                    items(appPages) { page ->
                                        val (displayName, route, description) = page
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                                .clickable {
                                                    navController.navigate(route)
                                                },
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Home Screen Selection
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Home Screen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val homeScreenOptions = listOf(
                        "dashboard" to "Spending Dashboard",
                        "message_browser" to "Message Browser"
                    )

                    homeScreenOptions.forEach { (screenId, screenName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedHomeScreen == screenId,
                                onClick = {
                                    selectedHomeScreen = screenId
                                    // setPreferredHomeScreen(screenId)
                                    // Toast.makeText(this@MainActivity, "Home screen updated to $screenName", Toast.LENGTH_SHORT).show()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = screenName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Transaction Filtering Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transaction Filtering",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showFilteringSettings = !showFilteringSettings }
                        ) {
                            Text(if (showFilteringSettings) "Hide" else "Edit")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Customize which SMS patterns are detected as transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (showFilteringSettings) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Filter Pattern Settings:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Enable or disable patterns used to identify financial SMS messages:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val allPatterns = listOf(
                            "%transaction%" to "Transaction keywords",
                            "%payment%" to "Payment keywords",
                            "%debit%" to "Debit notifications",
                            "%credit%" to "Credit notifications",
                            "%amount%" to "Amount mentions",
                            "%rs%" to "Rupee symbol (Rs)",
                            "%₹%" to "Rupee symbol (₹)",
                            "%inr%" to "INR currency",
                            "%debited%" to "Debited transactions"
                        )

                        LazyColumn(
                            modifier = Modifier.height(250.dp)
                        ) {
                            items(allPatterns) { (pattern, description) ->
                                val isEnabled = pattern in enabledPatterns

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isEnabled)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = pattern.removeSurrounding("%"),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isEnabled)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isEnabled)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Checkbox(
                                            checked = isEnabled,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    enabledPatterns = enabledPatterns + pattern
                                                } else {
                                                    enabledPatterns = enabledPatterns - pattern
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val enabledCount = enabledPatterns.size
                                val totalCount = allPatterns.size
                                // Toast.makeText(this@MainActivity, "$enabledCount of $totalCount patterns enabled. Changes will apply on next SMS analysis.", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💾 Save Pattern Settings")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "💡 Tip: Enable patterns that match the keywords and symbols in your bank's SMS messages. This helps the app identify financial transactions accurately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Custom Filter Patterns Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom Filter Patterns",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showCustomFilters = !showCustomFilters }
                        ) {
                            Text(if (showCustomFilters) "Hide" else "Manage")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Add your own custom patterns to detect transaction SMS messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (showCustomFilters) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Add new custom filter
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCustomFilter,
                                onValueChange = { newCustomFilter = it },
                                label = { Text("New pattern") },
                                placeholder = { Text("e.g., %credited% or %debited%") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (newCustomFilter.isNotBlank()) {
                                        val pattern = if (newCustomFilter.startsWith("%") && newCustomFilter.endsWith("%")) {
                                            newCustomFilter
                                        } else {
                                            "%${newCustomFilter}%"
                                        }
                                        customPatterns = customPatterns + pattern
                                        enabledPatterns = enabledPatterns + pattern
                                        newCustomFilter = ""
                                        // Toast.makeText(context, "Custom pattern added: $pattern", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = newCustomFilter.isNotBlank()
                            ) {
                                Text("Add")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Your Custom Patterns:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        if (customPatterns.isEmpty()) {
                            Text(
                                text = "No custom patterns added yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            customPatterns.forEach { pattern ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pattern.removeSurrounding("%"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )

                                        IconButton(
                                            onClick = {
                                                customPatterns = customPatterns - pattern
                                                enabledPatterns = enabledPatterns - pattern
                                            }
                                        ) {
                                            Text(
                                                "✕",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "💡 Tips for Custom Patterns:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Use % at start and end (e.g., %credited%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Patterns are case-insensitive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Test your patterns with different SMS formats",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Info section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "SMS Analytics App v1.0",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Analyze your SMS messages for spending insights",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}