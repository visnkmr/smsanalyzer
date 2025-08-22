package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.widget.Toast
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import com.smsanalytics.smstransactionanalyzer.parser.SMSParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterTestingScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smsReader = remember { SMSReader(context) }
    val parser = remember { SMSParser() }

    var testPattern by remember { mutableStateOf("") }
    var testResults by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
    var isTestingPattern by remember { mutableStateOf(false) }

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
                text = "🧪 Filter Pattern Testing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { navController.navigateUp() }) {
                Text("❌", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Test your filter patterns against actual SMS messages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test pattern input
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Enter Pattern to Test",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testPattern,
                        onValueChange = { testPattern = it },
                        label = { Text("Test pattern") },
                        placeholder = { Text("e.g., %credited% or %debited%") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (testPattern.isNotBlank() && smsReader.hasSMSPermission()) {
                                isTestingPattern = true
                                scope.launch {
                                    try {
                                        val allMessages = withContext(Dispatchers.IO) {
                                            smsReader.readAllSMS()
                                        }

                                        val pattern = if (testPattern.startsWith("%") && testPattern.endsWith("%")) {
                                            testPattern
                                        } else {
                                            "%${testPattern}%"
                                        }

                                        val matchedMessages = allMessages.filter { message ->
                                            message.body.contains(pattern.removeSurrounding("%"), ignoreCase = true) ||
                                            parser.isTransactionSMS(message.body)
                                        }

                                        testResults = matchedMessages
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error testing pattern: ${e.message}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isTestingPattern = false
                                    }
                                }
                            } else if (!smsReader.hasSMSPermission()) {
                                Toast.makeText(context, "SMS permission required", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = testPattern.isNotBlank() && !isTestingPattern
                    ) {
                        Text(if (isTestingPattern) "..." else "Test")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test results
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Test Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isTestingPattern) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing pattern against SMS messages...")
                    }
                } else if (testResults.isNotEmpty()) {
                    Text(
                        text = "Found ${testResults.size} matching messages:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(testResults.take(10)) { message ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        // Navigate to message detail
                                        navController.navigate("message_detail/${message.id}")
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = message.sender,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = message.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        if (testResults.size > 10) {
                            item {
                                Text(
                                    text = "... and ${testResults.size - 10} more messages",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (testPattern.isNotBlank()) {
                                // Save the pattern to SharedPreferences and return to settings
                                val pattern = if (testPattern.startsWith("%") && testPattern.endsWith("%")) {
                                    testPattern
                                } else {
                                    "%${testPattern}%"
                                }

                                // Save to SharedPreferences
                                val sharedPreferences = context.getSharedPreferences("sms_filter_settings", Context.MODE_PRIVATE)
                                val existingCustomPatterns = sharedPreferences.getStringSet("custom_patterns", emptySet()) ?: emptySet()
                                val updatedCustomPatterns = existingCustomPatterns + pattern

                                sharedPreferences.edit().putStringSet("custom_patterns", updatedCustomPatterns).apply()

                                Toast.makeText(context, "Pattern added to custom filters", Toast.LENGTH_SHORT).show()
                                navController.navigateUp() // Go back to settings
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = testPattern.isNotBlank()
                    ) {
                        Text("✅ Add This Pattern to Custom Filters")
                    }
                } else if (testPattern.isNotBlank() && !isTestingPattern) {
                    Text(
                        text = "No messages found matching this pattern",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Enter a pattern above and click 'Test' to see matching messages",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tips section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 Testing Tips:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Test patterns before adding them to custom filters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Check if the pattern matches real SMS messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Use % at start and end for partial matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• Examples: %credited%, %debited%, %payment%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}