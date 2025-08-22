package com.smsanalytics.smstransactionanalyzer.ui.screens

import androidx.compose.foundation.layout.*
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
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    navController: NavController,
    messageId: Long,
    smsReader: SMSReader
) {
    var message by remember { mutableStateOf<SMSReader.SMSMessage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(messageId) {
        try {
            val smsMessage = smsReader.getSMSMessageById(messageId)
            if (smsMessage != null) {
                message = smsMessage
                isLoading = false
            } else {
                error = "Message not found"
                isLoading = false
            }
        } catch (e: Exception) {
            error = e.message ?: "Unknown error"
            isLoading = false
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
                text = "📱 Message Details",
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading message details...")
                }
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ Error loading message",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.navigateUp() }) {
                        Text("Go Back")
                    }
                }
            }
        } else if (message != null) {
            MessageDetailContent(message = message!!)
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Message not found",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailContent(message: SMSReader.SMSMessage) {
    val scrollState = rememberScrollState()
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy 'at' hh:mm:ss a", Locale.getDefault()) }
    val sentDateFormat = remember { SimpleDateFormat("MMM dd, yyyy 'at' hh:mm:ss a", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Message Content Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📨 Message Content",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Message Body
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Basic Information Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 Basic Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow("Message ID", message.id.toString())
                DetailRow("Sender", message.sender)
                DetailRow("Address", message.address)
                DetailRow("Type", getMessageTypeText(message.type))
                DetailRow("Timestamp", dateFormat.format(Date(message.timestamp)))
                DetailRow("Thread ID", message.threadId.toString())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Information Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📱 Status Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                DetailRow("Read Status", if (message.read) "Read ✓" else "Unread")
                DetailRow("Seen Status", if (message.seen) "Seen ✓" else "Not Seen")
                DetailRow("Locked", if (message.locked) "Locked 🔒" else "Unlocked")
                DetailRow("Status", getDeliveryStatusText(message.status))
                DetailRow("Error Code", if (message.errorCode != 0) message.errorCode.toString() else "None")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Technical Information Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔧 Technical Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (message.protocol != null) {
                    DetailRow("Protocol", message.protocol.toString())
                }
                if (message.serviceCenter != null) {
                    DetailRow("Service Center", message.serviceCenter!!)
                }
                if (message.subject != null) {
                    DetailRow("Subject", message.subject!!)
                }
                if (message.dateSent > 0) {
                    DetailRow("Date Sent", sentDateFormat.format(Date(message.dateSent)))
                }
                if (message.creator != null) {
                    DetailRow("Creator", message.creator!!)
                }
                DetailRow("Subscription ID", message.subId.toString())
                DetailRow("Reply Path Present", if (message.replyPathPresent) "Yes" else "No")
                DetailRow("Priority", getPriorityText(message.priority))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "⚡ Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* Copy message */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📋 Copy")
                    }
                    OutlinedButton(
                        onClick = { /* Share message */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📤 Share")
                    }
                    OutlinedButton(
                        onClick = { /* Delete message */ },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("🗑️ Delete")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun getMessageTypeText(type: Int): String = when (type) {
    1 -> "Inbox 📥"
    2 -> "Sent 📤"
    3 -> "Draft 📝"
    4 -> "Outbox 📮"
    5 -> "Failed ❌"
    6 -> "Queued ⏳"
    else -> "Unknown ❓"
}

private fun getDeliveryStatusText(status: Int): String = when (status) {
    -1 -> "None"
    0 -> "Complete ✓"
    32 -> "Pending ⏳"
    64 -> "Failed ❌"
    else -> "Unknown ($status)"
}

private fun getPriorityText(priority: Int): String = when (priority) {
    0 -> "Normal"
    1 -> "High"
    2 -> "Low"
    else -> "Unknown"
}