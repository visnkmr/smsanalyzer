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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import com.smsanalytics.smstransactionanalyzer.model.ExcludedMessage

enum class TimeFilter {
    TODAY, THIS_WEEK, THIS_MONTH, CUSTOM_RANGE, ALL_TIME
}

data class DateRange(
    val startDate: Date,
    val endDate: Date
)

data class MonthSummary(
    val month: String,
    val messageCount: Int,
    val totalAmount: Double,
    val messages: List<SMSReader.SMSMessage>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBrowserScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
    var filteredMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
    var monthSummaries by remember { mutableStateOf<List<MonthSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(TimeFilter.ALL_TIME) }
    var customDateRange by remember { mutableStateOf<DateRange?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMonthDropdown by remember { mutableStateOf<String?>(null) }
    var excludedMessageIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val smsReader = remember { SMSReader(context) }
    val database = remember { SMSDatabase.getInstance(context) }
    val excludedMessageDao = remember { database.excludedMessageDao() }

    // Load excluded message IDs
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                excludedMessageIds = excludedMessageDao.getAllExcludedMessageIds().toSet()
            } catch (e: Exception) {
                // Handle error silently for now
            }
        }
    }

    // Load all messages on launch
    LaunchedEffect(Unit) {
        if (smsReader.hasSMSPermission()) {
            scope.launch {
                try {
                    allMessages = smsReader.readAllSMS()
                    monthSummaries = createMonthSummaries(allMessages)
                    filteredMessages = allMessages.sortedByDescending { it.timestamp }
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

    // Filter messages when tab changes or search query changes
    LaunchedEffect(selectedTab, customDateRange, allMessages, searchQuery) {
        var filtered = filterMessages(selectedTab, allMessages, customDateRange)

        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { message ->
                try {
                    // Try as regex first
                    val regex = Regex(searchQuery, setOf(RegexOption.IGNORE_CASE))
                    regex.containsMatchIn(message.body) ||
                    regex.containsMatchIn(message.sender)
                } catch (e: Exception) {
                    // Fall back to simple text search
                    message.body.contains(searchQuery, ignoreCase = true) ||
                    message.sender.contains(searchQuery, ignoreCase = true)
                }
            }
        }

        filteredMessages = filtered
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "Message Browser",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        // Search Bar
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search messages...") },
                    placeholder = { Text("Enter words or regex") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DateRange, // You can use a search icon
                            contentDescription = "Search"
                        )
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        isSearchActive = false
                    }) {
                        Text("✕", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

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
                                TimeFilter.CUSTOM_RANGE -> "Custom"
                                TimeFilter.ALL_TIME -> "All Time"
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Select Date")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Dates")
                        }

                        Button(
                            onClick = {
                                // Quick preset: Last 30 days
                                val calendar = Calendar.getInstance()
                                val endTime = calendar.time
                                calendar.add(Calendar.DAY_OF_MONTH, -30)
                                val startTime = calendar.time

                                customDateRange = DateRange(startTime, endTime)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Last 30 Days")
                        }
                    }

                    if (customDateRange != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Selected: ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(customDateRange!!.startDate)} - ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(customDateRange!!.endDate)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Month summaries dropdown
        if (monthSummaries.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Monthly Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(monthSummaries) { summary ->
                            MonthSummaryCard(
                                summary = summary,
                                isExpanded = showMonthDropdown == summary.month,
                                onToggle = {
                                    showMonthDropdown = if (showMonthDropdown == summary.month) null else summary.month
                                }
                            )
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
                    MessageItem(
                        message = message,
                        isExcluded = excludedMessageIds.contains(message.id),
                        onExcludeToggle = { exclude ->
                            scope.launch {
                                if (exclude) {
                                    val excludedMessage = ExcludedMessage(
                                        messageId = message.id,
                                        body = message.body,
                                        sender = message.sender,
                                        timestamp = message.timestamp
                                    )
                                    excludedMessageDao.insertExcludedMessage(excludedMessage)
                                    excludedMessageIds = excludedMessageIds + message.id
                                } else {
                                    excludedMessageDao.deleteExcludedMessageById(message.id)
                                    excludedMessageIds = excludedMessageIds - message.id
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        DateRangePickerDialog(
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { start, end ->
                customDateRange = DateRange(start, end)
                showDatePicker = false
            }
        )
    }
}

@Composable
fun MessageItem(
    message: SMSReader.SMSMessage,
    isExcluded: Boolean = false,
    onExcludeToggle: ((Boolean) -> Unit)? = null
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()) }
    val typeText = when (message.type) {
        1 -> "Inbox"
        2 -> "Sent"
        else -> "Unknown"
    }

    // Simple transaction detection (you can make this more sophisticated)
    val hasTransaction = message.body.contains(Regex("\\b\\d+[,.]?\\d*\\s*(?:INR|Rs|₹|USD|\\$)\\b", setOf(RegexOption.IGNORE_CASE)))
    val transactionAmount = if (hasTransaction) {
        Regex("\\b(\\d+[,.]?\\d*)\\s*(?:INR|Rs|₹|USD|\\$)\\b", setOf(RegexOption.IGNORE_CASE))
            .find(message.body)?.groupValues?.get(1) ?: ""
    } else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        border = BorderStroke(
            1.dp,
            if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with sender, type, and badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )

                    // Transaction badge
                    if (hasTransaction && transactionAmount.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "₹$transactionAmount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Excluded badge
                    if (isExcluded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EXCLUDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }

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

            // Action buttons
            if (onExcludeToggle != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onExcludeToggle(!isExcluded) }
                    ) {
                        Text(
                            text = if (isExcluded) "Include" else "Exclude",
                            color = if (isExcluded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
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

        TimeFilter.ALL_TIME -> allMessages
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
        TimeFilter.ALL_TIME -> "All messages"
    }
}

private fun createMonthSummaries(messages: List<SMSReader.SMSMessage>): List<MonthSummary> {
    val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
    val groupedMessages = messages.groupBy { message ->
        monthFormat.format(Date(message.timestamp))
    }

    return groupedMessages.map { (month, msgs) ->
        MonthSummary(
            month = month,
            messageCount = msgs.size,
            totalAmount = 0.0, // TODO: Calculate actual amounts from transaction analysis
            messages = msgs.sortedByDescending { it.timestamp }
        )
    }.sortedByDescending { summary ->
        monthFormat.parse(summary.month)?.time ?: 0L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSummaryCard(
    summary: MonthSummary,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(200.dp)
            .clickable { onToggle() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = summary.month,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${summary.messageCount} messages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Messages:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.height(150.dp)
                ) {
                    items(summary.messages) { message ->
                        MessageItem(message)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (Date, Date) -> Unit
) {
    var startDate by remember { mutableStateOf<Long?>(null) }
    var endDate by remember { mutableStateOf<Long?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Select Date Range",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Simple date selection (in a real app, you'd use a proper date picker)
                Text(
                    text = "Start Date: ${startDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it)) } ?: "Not selected"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedButton(onClick = {
                    // Set start date to 7 days ago
                    startDate = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_MONTH, -7)
                    }.time.time
                }) {
                    Text("Last 7 Days")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "End Date: ${endDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(it)) } ?: "Not selected"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedButton(onClick = {
                    // Set end date to today
                    endDate = Calendar.getInstance().time.time
                }) {
                    Text("Today")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (startDate != null && endDate != null) {
                                onDateRangeSelected(Date(startDate!!), Date(endDate!!))
                            }
                        },
                        enabled = startDate != null && endDate != null
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}
