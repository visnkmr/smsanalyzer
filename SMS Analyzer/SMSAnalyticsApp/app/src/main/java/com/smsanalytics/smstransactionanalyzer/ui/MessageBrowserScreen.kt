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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.lifecycleScope
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
import com.smsanalytics.smstransactionanalyzer.parser.SMSParser

enum class TimeFilter {
    TODAY, THIS_WEEK, THIS_MONTH, CUSTOM_RANGE, ALL_TIME
}

enum class SMSFilterMode {
    ALL_MESSAGES,        // Show all SMS
    SENDER_SPECIFIC,     // Show SMS from specific sender
    VENDOR_SPECIFIC,     // Show SMS from specific vendor
    TRANSACTION_ONLY,    // Show only OTP-verified transaction SMS
    NON_TRANSACTION_ONLY, // Show only non-transaction SMS
    DATE_SPECIFIC,       // Show SMS from specific date
    MONTH_SPECIFIC,      // Show SMS from specific month
    YEAR_SPECIFIC        // Show SMS from specific year
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

// Top level functions for unified SMS listing
fun getModeTitle(mode: SMSFilterMode): String {
    return when (mode) {
        SMSFilterMode.ALL_MESSAGES -> "All Messages"
        SMSFilterMode.SENDER_SPECIFIC -> "Messages by Sender"
        SMSFilterMode.VENDOR_SPECIFIC -> "Messages by Vendor"
        SMSFilterMode.TRANSACTION_ONLY -> "Transaction Messages"
        SMSFilterMode.NON_TRANSACTION_ONLY -> "Non-Transaction Messages"
        SMSFilterMode.DATE_SPECIFIC -> "Messages by Date"
        SMSFilterMode.MONTH_SPECIFIC -> "Messages by Month"
        SMSFilterMode.YEAR_SPECIFIC -> "Messages by Year"
    }
}

fun getModeDisplayName(mode: SMSFilterMode): String {
    return when (mode) {
        SMSFilterMode.ALL_MESSAGES -> "All"
        SMSFilterMode.SENDER_SPECIFIC -> "By Sender"
        SMSFilterMode.VENDOR_SPECIFIC -> "By Vendor"
        SMSFilterMode.TRANSACTION_ONLY -> "Transactions"
        SMSFilterMode.NON_TRANSACTION_ONLY -> "Non-Transactions"
        SMSFilterMode.DATE_SPECIFIC -> "By Date"
        SMSFilterMode.MONTH_SPECIFIC -> "By Month"
        SMSFilterMode.YEAR_SPECIFIC -> "By Year"
    }
}

suspend fun bulkExcludeSelectedMessages(
    messageIds: List<Long>,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    database: SMSDatabase,
    allMessages: List<SMSReader.SMSMessage>
) {
    scope.launch {
        try {
            val excludedMessageDao = database.excludedMessageDao()

            messageIds.forEach { messageId ->
                // Find message details from all messages list
                val message = allMessages.find { it.id == messageId }
                if (message != null) {
                    val excludedMessage = ExcludedMessage(
                        messageId = messageId,
                        body = message.body,
                        sender = message.sender,
                        timestamp = message.timestamp
                    )

                    excludedMessageDao.insertExcludedMessage(excludedMessage)
                }
            }

            Toast.makeText(context, "${messageIds.size} messages excluded successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error bulk excluding messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedMessageItem(
    message: SMSReader.SMSMessage,
    isSelected: Boolean = false,
    isSelectMode: Boolean = false,
    onSelectionChanged: ((Boolean) -> Unit)? = null,
    isTransactionSMS: Boolean = false
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable {
                if (isSelectMode && onSelectionChanged != null) {
                    onSelectionChanged(!isSelected)
                }
            },
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with checkbox (if in select mode), sender, and type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectionChanged?.invoke(it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isTransactionSMS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (isTransactionSMS) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Transaction",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Text(
                    text = when (message.type) {
                        1 -> "Inbox"
                        2 -> "Sent"
                        else -> "Unknown"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (message.type) {
                        1 -> Color(0xFF4CAF50)
                        2 -> Color(0xFF2196F3)
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

suspend fun getTotalMessageCount(context: android.content.Context): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val contentResolver = context.contentResolver
    val smsUri = android.net.Uri.parse("content://sms/")

    return@withContext try {
        contentResolver.query(smsUri, arrayOf("COUNT(*)"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else 0
        } ?: 0
    } catch (e: Exception) {
        0
    }
}

suspend fun loadMessageBatch(context: android.content.Context, offset: Int, limit: Int): List<SMSReader.SMSMessage> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val contentResolver = context.contentResolver
    val smsUri = android.net.Uri.parse("content://sms/")

    val projection = arrayOf(
        android.provider.Telephony.Sms._ID,
        android.provider.Telephony.Sms.BODY,
        android.provider.Telephony.Sms.ADDRESS,
        android.provider.Telephony.Sms.DATE,
        android.provider.Telephony.Sms.TYPE
    )

    return@withContext try {
        val messages = mutableListOf<SMSReader.SMSMessage>()

        contentResolver.query(
            smsUri,
            projection,
            null,
            null,
            "${android.provider.Telephony.Sms.DATE} DESC LIMIT $limit OFFSET $offset"
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val smsBody = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.Telephony.Sms.BODY))
                    val sender = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.Telephony.Sms.ADDRESS))
                    val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.Telephony.Sms.DATE))
                    val type = cursor.getInt(cursor.getColumnIndexOrThrow(android.provider.Telephony.Sms.TYPE))

                    messages.add(SMSReader.SMSMessage(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.Telephony.Sms._ID)),
                        body = smsBody,
                        sender = sender,
                        timestamp = timestamp,
                        type = type
                    ))
                } catch (e: Exception) {
                    continue
                }
            }
        }

        messages
    } catch (e: Exception) {
        emptyList()
    }
}

fun bulkExcludeMessages(
    messagesToExclude: List<SMSReader.SMSMessage>,
    excludedMessageIds: Set<Long>,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    database: SMSDatabase,
    excludedMessageDao: com.smsanalytics.smstransactionanalyzer.database.ExcludedMessageDao
) {
    scope.launch {
        try {
            val activeMessages = messagesToExclude.filter { !excludedMessageIds.contains(it.id) }
            if (activeMessages.isEmpty()) {
                Toast.makeText(context, "No messages to exclude", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Add all messages to excluded list
            activeMessages.forEach<SMSReader.SMSMessage> { message ->
                scope.launch {
                    val excludedMessage = ExcludedMessage(
                        messageId = message.id,
                        body = message.body,
                        sender = message.sender,
                        timestamp = message.timestamp
                    )
                    excludedMessageDao.insertExcludedMessage(excludedMessage)
                }
            }

            Toast.makeText(context, "${activeMessages.size} messages excluded successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error bulk excluding messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun performSmartSearch(message: SMSReader.SMSMessage, query: String): Boolean {
    // Handle different search patterns
    return when {
        // Check if it's a regex pattern (contains regex special characters)
        query.contains(Regex("[\\[\\]{}()*+?.\\\\^$|]")) -> {
            try {
                val regex = Regex(query, setOf(RegexOption.IGNORE_CASE))
                regex.containsMatchIn(message.body) ||
                regex.containsMatchIn(message.sender)
            } catch (e: Exception) {
                // Fall back to literal text search if regex is invalid
                performLiteralSearch(message, query)
            }
        }
        // Check for phone number search
        query.matches(Regex("\\d{10,}")) -> {
            message.sender.contains(query, ignoreCase = true)
        }
        // Check for amount search (₹, Rs, INR, etc.)
        query.matches(Regex("\\d+[,.]?\\d*")) -> {
            val amountRegex = Regex("\\b$query\\s*(?:INR|Rs|₹|USD|\\$)\\b|\\b(?:INR|Rs|₹|USD|\\$)\\s*$query\\b", setOf(RegexOption.IGNORE_CASE))
            amountRegex.containsMatchIn(message.body)
        }
        // Default literal search
        else -> performLiteralSearch(message, query)
    }
}

private fun performLiteralSearch(message: SMSReader.SMSMessage, query: String): Boolean {
    return message.body.contains(query, ignoreCase = true) ||
           message.sender.contains(query, ignoreCase = true)
}

private fun shouldUseCachedData(lastAnalysis: com.smsanalytics.smstransactionanalyzer.model.AnalysisMetadata?, cachedTransactions: List<com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache>): Boolean {
    if (lastAnalysis == null || cachedTransactions.isEmpty()) return false

    // Use cache if analysis was done within the last hour
    val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
    return lastAnalysis.lastAnalysisDate > oneHourAgo
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageBrowserScreen(
    navController: androidx.navigation.NavController,
    filterMode: SMSFilterMode = SMSFilterMode.ALL_MESSAGES,
    filterValue: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var allMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
    var filteredMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
    var monthSummaries by remember { mutableStateOf<List<MonthSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(TimeFilter.ALL_TIME) }
    var customDateRange by remember { mutableStateOf<DateRange?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showMonthDropdown by remember { mutableStateOf<String?>(null) }
    var excludedMessageIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showBulkExcludeDialog by remember { mutableStateOf(false) }
    var loadedMessageCount by remember { mutableStateOf(0) }
    var totalMessageCount by remember { mutableStateOf(0) }

    // Filter parameters from navigation
    var currentFilterMode by remember { mutableStateOf(filterMode) }
    var currentFilterValue by remember { mutableStateOf(filterValue) }

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

    // Load ALL messages from SMS database (not just cached transactions)
    LaunchedEffect(Unit) {
        if (smsReader.hasSMSPermission()) {
            scope.launch {
                try {
                    isLoading = true

                    // Load ALL messages from SMS database using readAllSMS
                    val allSmsMessages = smsReader.readAllSMS()

                    if (allSmsMessages.isNotEmpty()) {
                        allMessages = allSmsMessages
                        loadedMessageCount = allSmsMessages.size
                        totalMessageCount = allSmsMessages.size
                        monthSummaries = createMonthSummaries(allSmsMessages)

                        // Apply current filters
                        var filtered = filterMessages(selectedTab, allMessages, customDateRange)
                        if (searchQuery.isNotEmpty()) {
                            filtered = filtered.filter { message ->
                                performSmartSearch(message, searchQuery)
                            }
                        }
                        filteredMessages = filtered.sortedByDescending { it.timestamp }

                        isLoading = false
                        return@launch
                    } else {
                        // No messages available
                        Toast.makeText(context, "No SMS messages found on device.", Toast.LENGTH_LONG).show()
                        isLoading = false
                        return@launch
                    }

                } catch (e: Exception) {
                    Toast.makeText(context, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                } finally {
                    isLoading = false
                    isLoadingMore = false
                }
            }
        } else {
            Toast.makeText(context, "SMS permission required", Toast.LENGTH_SHORT).show()
            isLoading = false
        }
    }

    // Filter messages when tab changes, search query changes, or filter parameters change
    LaunchedEffect(selectedTab, customDateRange, allMessages, searchQuery, currentFilterMode, currentFilterValue) {
        var filtered = filterMessages(selectedTab, allMessages, customDateRange)

        // Apply specific filter mode if provided
        when (currentFilterMode) {
            SMSFilterMode.SENDER_SPECIFIC -> {
                if (currentFilterValue != null) {
                    filtered = filtered.filter { it.sender == currentFilterValue }
                }
            }
            SMSFilterMode.VENDOR_SPECIFIC -> {
                if (currentFilterValue != null) {
                    filtered = filtered.filter { it.sender == currentFilterValue }
                }
            }
            SMSFilterMode.TRANSACTION_ONLY -> {
                filtered = filtered.filter { message ->
                    message.body.contains(Regex("\\b\\d+[,.]?\\d*\\s*(?:INR|Rs|₹|USD|\\$)\\b", setOf(RegexOption.IGNORE_CASE)))
                }
            }
            SMSFilterMode.NON_TRANSACTION_ONLY -> {
                filtered = filtered.filter { message ->
                    !message.body.contains(Regex("\\b\\d+[,.]?\\d*\\s*(?:INR|Rs|₹|USD|\\$)\\b", setOf(RegexOption.IGNORE_CASE)))
                }
            }
            else -> {
                // No additional filtering for ALL_MESSAGES or other modes
            }
        }

        // Apply search filter
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { message ->
                performSmartSearch(message, searchQuery)
            }
        }

        filteredMessages = filtered
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with navigation and bulk exclude buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentFilterValue != null) getModeTitle(currentFilterMode) else "Message Browser",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Clear filter button (only show if there's an active filter)
                if (currentFilterValue != null) {
                    IconButton(onClick = {
                        currentFilterMode = SMSFilterMode.ALL_MESSAGES
                        currentFilterValue = null
                    }) {
                        Text("✕", style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Navigation to spending dashboard
                IconButton(onClick = {
                    navController.navigate("dashboard") {
                        popUpTo("message_browser") { inclusive = false }
                    }
                }) {
                    Text("📊", style = MaterialTheme.typography.titleMedium)
                }

                if (filteredMessages.isNotEmpty()) {
                    IconButton(onClick = { showBulkExcludeDialog = true }) {
                        Text("🚫", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // Show filter value as subtitle if provided
        currentFilterValue?.let { filterValue ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = filterValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

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
                    placeholder = { Text("Enter text, phone numbers, amounts, or regex patterns") },
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
                    
                    @OptIn(ExperimentalMaterial3Api::class)
                    @Composable
                    fun UnifiedSMSListingPage(
                        navController: androidx.navigation.NavController,
                        initialMode: SMSFilterMode = SMSFilterMode.ALL_MESSAGES,
                        filterValue: String? = null // sender name, vendor name, date, etc.
                    ) {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                    
                        // State management
                        var allMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
                        var filteredMessages by remember { mutableStateOf<List<SMSReader.SMSMessage>>(emptyList()) }
                        var isLoading by remember { mutableStateOf(true) }
                        var selectedMessages by remember { mutableStateOf<Set<Long>>(emptySet()) }
                        var isSelectMode by remember { mutableStateOf(false) }
                    
                        // Filter states
                        var currentMode by remember { mutableStateOf(initialMode) }
                        var searchQuery by remember { mutableStateOf("") }
                        var selectedSender by remember { mutableStateOf(filterValue ?: "") }
                        var selectedDate by remember {
                            mutableStateOf(
                                when (initialMode) {
                                    SMSFilterMode.DATE_SPECIFIC -> {
                                        filterValue?.let { dateStr ->
                                            try {
                                                val parts = dateStr.split("-")
                                                if (parts.size >= 3) {
                                                    val year = parts[0].toInt()
                                                    val month = parts[1].toInt() - 1 // Calendar months are 0-based
                                                    val day = parts[2].toInt()
                                                    Calendar.getInstance().apply {
                                                        set(Calendar.YEAR, year)
                                                        set(Calendar.MONTH, month)
                                                        set(Calendar.DAY_OF_MONTH, day)
                                                        set(Calendar.HOUR_OF_DAY, 0)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }.time
                                                } else null
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                    }
                                    else -> null
                                }
                            )
                        }
                        var selectedMonth by remember {
                            mutableStateOf(
                                when (initialMode) {
                                    SMSFilterMode.YEAR_SPECIFIC -> filterValue
                                    SMSFilterMode.MONTH_SPECIFIC -> {
                                        filterValue?.let { monthStr ->
                                            try {
                                                val parts = monthStr.split("-")
                                                if (parts.size >= 2) {
                                                    val year = parts[0].toInt()
                                                    val month = parts[1].toInt()
                                                    // Convert to month name format (e.g., "December 2023")
                                                    val calendar = Calendar.getInstance()
                                                    calendar.set(Calendar.YEAR, year)
                                                    calendar.set(Calendar.MONTH, month - 1) // Calendar months are 0-based
                                                    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                                                    monthFormat.format(calendar.time)
                                                } else monthStr
                                            } catch (e: Exception) {
                                                monthStr
                                            }
                                        }
                                    }
                                    else -> null
                                }
                            )
                        }
                    
                        val smsReader = remember { SMSReader(context) }
                        val database = remember { SMSDatabase.getInstance(context) }
                        val parser = remember { SMSParser() }
                    
                        // Load messages from cache instead of reprocessing SMS
                        LaunchedEffect(Unit) {
                            if (smsReader.hasSMSPermission()) {
                                scope.launch {
                                    try {
                                        // Load from cache only - don't reprocess SMS
                                        val cachedTransactions = database.smsAnalysisCacheDao().getCachedTransactions()

                                        if (cachedTransactions.isNotEmpty()) {
                                            // Convert cached transactions to SMSMessage format
                                            allMessages = cachedTransactions.map { cache ->
                                                SMSReader.SMSMessage(
                                                    id = cache.messageId,
                                                    body = cache.messageBody,
                                                    sender = cache.sender,
                                                    timestamp = cache.timestamp,
                                                    type = 1 // Default to inbox
                                                )
                                            }
                                            isLoading = false
                                        } else {
                                            // No cached data available
                                            Toast.makeText(context, "No SMS data available. Run SMS analysis first.", Toast.LENGTH_LONG).show()
                                            isLoading = false
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
                                        isLoading = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "SMS permission required", Toast.LENGTH_SHORT).show()
                                isLoading = false
                            }
                        }
                    
                        // Filter messages based on current mode and search
                        LaunchedEffect(currentMode, searchQuery, selectedSender, selectedDate, selectedMonth, allMessages) {
                            var filtered = when (currentMode) {
                                SMSFilterMode.ALL_MESSAGES -> allMessages
                                SMSFilterMode.SENDER_SPECIFIC -> allMessages.filter { it.sender == selectedSender }
                                SMSFilterMode.VENDOR_SPECIFIC -> allMessages.filter { it.sender == selectedSender }
                                SMSFilterMode.TRANSACTION_ONLY -> allMessages.filter { parser.isTransactionSMS(it.body) }
                                SMSFilterMode.NON_TRANSACTION_ONLY -> allMessages.filter { !parser.isTransactionSMS(it.body) }
                                SMSFilterMode.DATE_SPECIFIC -> {
                                    if (selectedDate != null) {
                                        allMessages.filter { message ->
                                            val messageDate = Date(message.timestamp)
                                            messageDate.toString().substring(0, 10) == selectedDate.toString().substring(0, 10)
                                        }
                                    } else allMessages
                                }
                                SMSFilterMode.MONTH_SPECIFIC -> {
                                    if (selectedMonth != null) {
                                        allMessages.filter { message ->
                                            val messageDate = Date(message.timestamp)
                                            val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                                            monthFormat.format(messageDate) == selectedMonth
                                        }
                                    } else allMessages
                                }
                                SMSFilterMode.YEAR_SPECIFIC -> {
                                    if (selectedMonth != null) { // Using selectedMonth for year filter
                                        allMessages.filter { message ->
                                            val messageDate = Date(message.timestamp)
                                            val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())
                                            yearFormat.format(messageDate) == selectedMonth
                                        }
                                    } else allMessages
                                }
                            }
                    
                            // Apply search filter
                            if (searchQuery.isNotEmpty()) {
                                filtered = filtered.filter { message ->
                                    message.body.contains(searchQuery, ignoreCase = true) ||
                                    message.sender.contains(searchQuery, ignoreCase = true)
                                }
                            }
                    
                            filteredMessages = filtered.sortedByDescending { it.timestamp }
                        }
                    
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header with mode info and actions
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = getModeTitle(currentMode),
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (filterValue != null) {
                                        Text(
                                            text = filterValue,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                    
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelectMode && filteredMessages.isNotEmpty()) {
                                        IconButton(onClick = {
                                            selectedMessages = filteredMessages.map { it.id }.toSet()
                                        }) {
                                            Text("☑️", style = MaterialTheme.typography.titleMedium)
                                        }
                                        IconButton(onClick = {
                                            // Bulk exclude selected messages
                                            scope.launch {
                                                bulkExcludeSelectedMessages(selectedMessages.toList(), context, scope, database, allMessages)
                                            }
                                            selectedMessages = emptySet()
                                            isSelectMode = false
                                        }) {
                                            Text("🚫", style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                    
                                    IconButton(onClick = { isSelectMode = !isSelectMode }) {
                                        Text(if (isSelectMode) "✕" else "☑️", style = MaterialTheme.typography.titleMedium)
                                    }
                    
                                    IconButton(onClick = { navController.navigate("dashboard") }) {
                                        Text("←")
                                    }
                                }
                            }
                    
                            // Mode selection chips
                            LazyRow(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(SMSFilterMode.values()) { mode ->
                                    FilterChip(
                                        selected = currentMode == mode,
                                        onClick = {
                                            currentMode = mode
                                            // Reset filter values when mode changes
                                            selectedSender = ""
                                            selectedDate = null
                                            selectedMonth = null
                                        },
                                        label = { Text(getModeDisplayName(mode)) }
                                    )
                                }
                            }
                    
                            // Search bar
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
                                        placeholder = { Text("Enter text to search") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        leadingIcon = { Text("🔍") }
                                    )
                    
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Text("✕", style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                    
                            // Mode-specific filters
                            when (currentMode) {
                                SMSFilterMode.SENDER_SPECIFIC, SMSFilterMode.VENDOR_SPECIFIC -> {
                                    // Sender/Vendor selection
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Select ${if (currentMode == SMSFilterMode.SENDER_SPECIFIC) "Sender" else "Vendor"}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                    
                                            Spacer(modifier = Modifier.height(8.dp))
                    
                                            // Get unique senders/vendors
                                            val uniqueSenders = allMessages.map { it.sender }.distinct().sorted()
                    
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(uniqueSenders) { sender ->
                                                    FilterChip(
                                                        selected = selectedSender == sender,
                                                        onClick = { selectedSender = sender },
                                                        label = { Text(sender) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                    
                                SMSFilterMode.DATE_SPECIFIC -> {
                                    // Date picker
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Select Date",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                    
                                            Spacer(modifier = Modifier.height(8.dp))
                    
                                            Button(onClick = {
                                                // Show date picker dialog
                                                // For now, just set to today
                                                selectedDate = Date()
                                            }) {
                                                Text(selectedDate?.let {
                                                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it)
                                                } ?: "Pick Date")
                                            }
                                        }
                                    }
                                }
                    
                                SMSFilterMode.MONTH_SPECIFIC -> {
                                    // Month selection
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Select Month",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                    
                                            Spacer(modifier = Modifier.height(8.dp))
                    
                                            val availableMonths = allMessages.map { message ->
                                                val messageDate = Date(message.timestamp)
                                                val monthFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
                                                monthFormat.format(messageDate)
                                            }.distinct().sorted()
                    
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                items(availableMonths) { month ->
                                                    FilterChip(
                                                        selected = selectedMonth == month,
                                                        onClick = { selectedMonth = month },
                                                        label = { Text(month) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                    
                                else -> {} // No additional filters needed
                            }
                    
                            // Results count
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
                    
                                    if (isSelectMode) {
                                        Text(
                                            text = "${selectedMessages.size} selected",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                    
                            // Messages list
                            if (isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
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
                                            text = "Try changing the filter or search criteria",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredMessages) { message ->
                                        UnifiedMessageItem(
                                            message = message,
                                            isSelected = selectedMessages.contains(message.id),
                                            isSelectMode = isSelectMode,
                                            onSelectionChanged = { selected: Boolean ->
                                                selectedMessages = if (selected) {
                                                    selectedMessages + message.id
                                                } else {
                                                    selectedMessages - message.id
                                                }
                                            },
                                            isTransactionSMS = parser.isTransactionSMS(message.body)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    
                    
                }
            }
        }

        // Messages count and filter info with loading progress
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                // Show loading progress
                if (isLoading || isLoadingMore) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinearProgressIndicator(
                            progress = if (totalMessageCount > 0) loadedMessageCount.toFloat() / totalMessageCount.toFloat() else 0f,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLoading) "Loading..." else "Loading more...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "$loadedMessageCount / $totalMessageCount messages loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
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

        // Bulk exclude dialog
        if (showBulkExcludeDialog) {
            AlertDialog(
                onDismissRequest = { showBulkExcludeDialog = false },
                title = { Text("Exclude All Filtered Messages") },
                text = {
                    Text("Are you sure you want to exclude all ${filteredMessages.size} filtered messages from analysis? This will prevent them from being counted in future spending calculations.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            bulkExcludeMessages(
                                filteredMessages,
                                excludedMessageIds,
                                context,
                                scope,
                                database,
                                excludedMessageDao
                            )
                            showBulkExcludeDialog = false
                        }
                    ) {
                        Text("Exclude All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkExcludeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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

                    // Transaction badge with amount
                    if (hasTransaction && transactionAmount.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "₹$transactionAmount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Transaction indicator (without amount)
                    if (hasTransaction && transactionAmount.isEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Transaction",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary
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
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold
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
                    // Include button (only show when excluded)
                    if (isExcluded) {
                        TextButton(
                            onClick = { onExcludeToggle(false) }
                        ) {
                            Text(
                                text = "Include",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Exclude button (only show when not excluded)
                    if (!isExcluded) {
                        TextButton(
                            onClick = { onExcludeToggle(true) }
                        ) {
                            Text(
                                text = "Exclude",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Transaction-specific actions
                    if (hasTransaction) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                // Could add more transaction-specific actions here
                                // For now, just show it's a transaction
                            }
                        ) {
                            Text(
                                text = "💰",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
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
