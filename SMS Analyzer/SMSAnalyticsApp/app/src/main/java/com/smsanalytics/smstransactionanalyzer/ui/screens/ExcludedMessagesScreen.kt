package com.smsanalytics.smstransactionanalyzer.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.smsanalytics.smstransactionanalyzer.database.SMSDatabase
import com.smsanalytics.smstransactionanalyzer.model.ExcludedMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExcludedMessagesScreen(navController: androidx.navigation.NavController) {
    var excludedMessages by remember { mutableStateOf<List<ExcludedMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SMSDatabase.getInstance(context) }

    // Load excluded messages when screen is displayed
    LaunchedEffect(Unit) {
        try {
            val messages = database.excludedMessageDao().getAllExcludedMessages()
            messages.collect { messageList ->
                excludedMessages = messageList
                isLoading = false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error loading excluded messages", Toast.LENGTH_SHORT).show()
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
                text = "Excluded Messages",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { navController.navigate("dashboard") }) {
                Text("←")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (excludedMessages.isEmpty()) {
            Text(
                text = "No excluded messages found",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(excludedMessages) { excludedMessage ->
                    ExcludedMessageItem(excludedMessage, navController)
                }
            }
        }
    }
}

@Composable
fun ExcludedMessageItem(excludedMessage: ExcludedMessage, navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SMSDatabase.getInstance(context) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                navController.navigate("sender_sms_detail/${java.net.URLEncoder.encode(excludedMessage.sender, "UTF-8")}")
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = excludedMessage.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "From: ${excludedMessage.sender}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Excluded: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(excludedMessage.excludedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { restoreExcludedMessage(excludedMessage, context, scope, database) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Restore")
                }
            }
        }
    }
}

private fun restoreExcludedMessage(
    excludedMessage: ExcludedMessage,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    database: SMSDatabase
) {
    scope.launch {
        try {
            // Remove from excluded messages
            database.excludedMessageDao().deleteExcludedMessage(excludedMessage)

            // If it exists in cache, mark as not excluded
            database.smsAnalysisCacheDao().markMessageAsIncluded(excludedMessage.messageId)

            Toast.makeText(context, "Message restored to analysis", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error restoring message: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}