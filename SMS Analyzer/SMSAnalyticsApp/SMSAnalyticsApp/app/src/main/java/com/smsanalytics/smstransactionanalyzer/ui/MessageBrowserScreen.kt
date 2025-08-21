package com.smsanalytics.smstransactionanalyzer.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.smsanalytics.smstransactionanalyzer.database.SMSDatabase
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.graphics.Color

enum class TimeFilter {
    TODAY, THIS_WEEK, THIS_MONTH, CUSTOM_RANGE
}

data class DateRange(
    val startDate: Date,
    val endDate: Date
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBrowserScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
    var filteredMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(TimeFilter.TODAY) }
    var customDateRange by remember { mutableStateOf<DateRange?>(null) }

    // Date picker state
    val dateRangePickerState = rememberDateRangePickerState()

    val smsReader = remember { SMSReader(context) }

    // Load all messages on launch
    LaunchedEffect(Unit) {
        if (smsReader.hasSMSPermission()) {
            scope.launch {
                try {
                    allMessages = smsReader.readAllSMS()
                    filterMessages(selectedTab, allMessages, customDateRange)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        } else {
            Toast.makeText(context, "SMS permission required", Toast.LENGTH_SHORT).show()
            isLoading = false
        }
    }

    // Filter messages when tab changes
    LaunchedEffect(selectedTab, customDateRange, allMessages) {
        filterMessages(selectedTab, allMessages, customDateRange)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "Message Browser",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        // Tab row
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            TimeFilter.values().forEach { filter ->
                Tab(
                    selected = selectedTab == filter,
                    onClick = {
                        selectedTab = filter
                        if (filter != TimeFilter.CUSTOM_RANGE) {
                            customDateRange = null
                        }
                    },
                    text = {
                        Text(
                            when (filter) {
                                TimeFilter.TODAY -> "Today"
                                TimeFilter.THIS_WEEK -> "This Week"
                                TimeFilter.THIS_MONTH -> "This Month"
                                TimeFilter.CUSTOM_RANGE -> "Custom Range"
                            }
                        )
                    }
                )
            }
        }

        // Custom date range picker
        if (selectedTab == TimeFilter.CUSTOM_RANGE) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Date Range",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    DateRangePicker(
                        state = dateRangePickerState,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                val startMillis = dateRangePickerState.selectedStartDateMillis
                                val endMillis = dateRangePickerState.selectedEndDateMillis

                                if (startMillis != null && endMillis != null) {
                                    customDateRange = DateRange(
                                        startDate = Date(startMillis),
                                        endDate = Date(endMillis)
                                    )
                                }
                            }
                        ) {
                            Text("Apply Range")
                        }
                    }
                }
            }
        }

        // Messages count and filter info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredMessages.size} messages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = getFilterDescription(selectedTab, customDateRange),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Messages list
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading messages...")
                }
            }
        } else if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No messages found",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Try changing the date filter",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredMessages) { message ->
                    MessageItem(message)
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: SMSReader.SMSMessage) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()) }
    val typeText = when (message.type) {
        1 -> "Inbox"
        2 -> "Sent"
        else -> "Unknown"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with sender and type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = typeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (message.type) {
                        1 -> Color(0xFF4CAF50) // Green for inbox
                        2 -> Color(0xFF2196F3) // Blue for sent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Date
            Text(
                text = dateFormat.format(Date(message.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Message content
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

private fun filterMessages(
    filter: TimeFilter,
    allMessages: List<SMSReader.SMSMessage>,
    customDateRange: DateRange?
): List<SMSReader.SMSMessage> {
    val calendar = Calendar.getInstance()
    val now = calendar.time

    return when (filter) {
        TimeFilter.TODAY -> {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfDay = calendar.time

            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val endOfDay = calendar.time

            allMessages.filter { message ->
                val messageDate = Date(message.timestamp)
                messageDate >= startOfDay && messageDate < endOfDay
            }
        }

        TimeFilter.THIS_WEEK -> {
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfWeek = calendar.time

            calendar.add(Calendar.WEEK_OF_YEAR, 1)
            val endOfWeek = calendar.time

            allMessages.filter { message ->
                val messageDate = Date(message.timestamp)
                messageDate >= startOfWeek && messageDate < endOfWeek
            }
        }

        TimeFilter.THIS_MONTH -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startOfMonth = calendar.time

            calendar.add(Calendar.MONTH, 1)
            val endOfMonth = calendar.time

            allMessages.filter { message ->
                val messageDate = Date(message.timestamp)
                messageDate >= startOfMonth && messageDate < endOfMonth
            }
        }

        TimeFilter.CUSTOM_RANGE -> {
            if (customDateRange != null) {
                allMessages.filter { message ->
                    val messageDate = Date(message.timestamp)
                    messageDate >= customDateRange.startDate && messageDate <= customDateRange.endDate
                }
            } else {
                allMessages
            }
        }
    }.sortedByDescending { it.timestamp }
}

private fun getFilterDescription(filter: TimeFilter, customDateRange: DateRange?): String {
    return when (filter) {
        TimeFilter.TODAY -> "Messages from today"
        TimeFilter.THIS_WEEK -> "Messages from this week"
        TimeFilter.THIS_MONTH -> "Messages from this month"
        TimeFilter.CUSTOM_RANGE -> {
            if (customDateRange != null) {
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                "${dateFormat.format(customDateRange.startDate)} - ${dateFormat.format(customDateRange.endDate)}"
            } else {
                "Select date range"
            }
        }
    }
}
