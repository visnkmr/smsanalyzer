package com.smsanalytics.smstransactionanalyzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smsanalytics.smstransactionanalyzer.calculator.SpendingCalculator
import com.smsanalytics.smstransactionanalyzer.export.ExportFormat
import com.smsanalytics.smstransactionanalyzer.export.ExportManager
import com.smsanalytics.smstransactionanalyzer.export.CompressionType
import com.smsanalytics.smstransactionanalyzer.model.DailySummary
import com.smsanalytics.smstransactionanalyzer.model.MonthlySummary
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.ExcludedMessage
import com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache
import com.smsanalytics.smstransactionanalyzer.model.AnalysisMetadata
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import com.smsanalytics.smstransactionanalyzer.ui.theme.SMSAnalyticsAppTheme
import com.smsanalytics.smstransactionanalyzer.ui.CategoryRulesScreen
import com.smsanalytics.smstransactionanalyzer.ui.MessageBrowserScreen
import com.smsanalytics.smstransactionanalyzer.database.SMSDatabase
import com.smsanalytics.smstransactionanalyzer.util.VendorExtractor
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class SortOption {
    AMOUNT_DESC, // Most spend
    DATE_ASC,    // Oldest first
    DATE_DESC    // Newest first (default)
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private lateinit var smsReader: SMSReader
    private lateinit var calculator: SpendingCalculator
    private lateinit var exportManager: ExportManager
    private lateinit var database: SMSDatabase
    private lateinit var vendorExtractor: VendorExtractor

    private var transactions by mutableStateOf<List<Transaction>>(emptyList())
    private var dailySummaries by mutableStateOf<List<DailySummary>>(emptyList())
    private var monthlySummaries by mutableStateOf<List<MonthlySummary>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var hasPermission by mutableStateOf(false)
    private var progressValue by mutableStateOf(0)
    private var progressMessage by mutableStateOf("")

    // Sort options
    private var dailySortOption by mutableStateOf(SortOption.DATE_DESC)
    private var monthlySortOption by mutableStateOf(SortOption.DATE_DESC)
    private var hasUnsavedChanges by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        if (isGranted) {
            loadTransactionData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        smsReader = SMSReader(this)
        calculator = SpendingCalculator()
        exportManager = ExportManager(this)
        database = SMSDatabase.getInstance(this)
        vendorExtractor = VendorExtractor()

        hasPermission = smsReader.hasSMSPermission()

        setContent {
            SMSAnalyticsAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        if (!hasPermission) {
            requestSMSPermission()
        } else {
            loadTransactionData()
        }
    }

    private fun requestSMSPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
    }

    private fun loadTransactionData() {
        isLoading = true
        progressValue = 0
        progressMessage = "Starting SMS analysis..."

        lifecycleScope.launch {
            try {
                // Step 1: Check for cached data first
                progressValue = 5
                progressMessage = "Checking for cached data..."
                kotlinx.coroutines.delay(100)

                val lastAnalysis = database.smsAnalysisCacheDao().getLastAnalysisMetadata()
                val cachedTransactions = database.smsAnalysisCacheDao().getCachedTransactions()
                val shouldUseCache = shouldUseCachedData(lastAnalysis, cachedTransactions)

                if (shouldUseCache && cachedTransactions.isNotEmpty()) {
                    // Use cached data
                    progressValue = 10
                    progressMessage = "Loading from cache..."
                    kotlinx.coroutines.delay(100)

                    transactions = cachedTransactions.map { cache ->
                        Transaction(
                            amount = cache.transactionAmount ?: 0.0,
                            type = cache.transactionType ?: com.smsanalytics.smstransactionanalyzer.model.TransactionType.DEBIT,
                            description = cache.messageBody,
                            date = Date(cache.timestamp),
                            smsBody = cache.messageBody,
                            sender = cache.sender
                        )
                    }

                    // Step 2: Calculate spending from cached data
                    progressValue = 20
                    progressMessage = "Calculating spending from cache..."
                    kotlinx.coroutines.delay(100)

                    dailySummaries = calculator.calculateDailySpending(transactions)

                    progressValue = 30
                    progressMessage = "Calculating monthly summaries..."
                    kotlinx.coroutines.delay(100)

                    monthlySummaries = calculator.calculateMonthlySpending(transactions)

                    progressValue = 90
                    progressMessage = "Data loaded from cache!"
                    kotlinx.coroutines.delay(300)

                    progressValue = 100
                    progressMessage = "Loaded ${transactions.size} transactions from cache"

                } else {
                    // Process fresh SMS data
                    progressValue = 10
                    progressMessage = "No valid cache found, processing SMS messages..."
                    kotlinx.coroutines.delay(100)

                    // Step 2: Read SMS with progress
                    val allTransactions = smsReader.readTransactionSMS { progressVal, message ->
                        progressValue = progressVal
                        progressMessage = message
                    }

                    // Step 3: Filter out excluded transactions
                    progressValue = 20
                    progressMessage = "Filtering excluded transactions..."
                    kotlinx.coroutines.delay(100)

                    val excludedMessageIds = database.excludedMessageDao().getAllExcludedMessageIds()
                    transactions = allTransactions.filter { transaction ->
                        !isTransactionExcluded(transaction, excludedMessageIds)
                    }

                    // Step 4: Update cache with new data
                    progressValue = 30
                    progressMessage = "Updating cache..."
                    kotlinx.coroutines.delay(100)

                    updateCacheWithTransactions(allTransactions)

                    // Step 5: Calculate spending
                    progressValue = 40
                    progressMessage = "Calculating daily spending for ${transactions.size} transactions..."
                    kotlinx.coroutines.delay(100)

                    dailySummaries = calculator.calculateDailySpending(transactions)

                    progressValue = 50
                    progressMessage = "Found ${dailySummaries.size} days with transactions"
                    kotlinx.coroutines.delay(100)

                    // Step 6: Calculate monthly spending
                    progressValue = 60
                    progressMessage = "Calculating monthly spending from ${dailySummaries.size} daily summaries..."
                    kotlinx.coroutines.delay(100)

                    monthlySummaries = calculator.calculateMonthlySpending(transactions)

                    progressValue = 70
                    progressMessage = "Completed analysis: ${dailySummaries.size} days, ${monthlySummaries.size} months"
                    kotlinx.coroutines.delay(100)

                    // Step 7: Save analysis metadata
                    progressValue = 80
                    progressMessage = "Saving analysis metadata..."
                    kotlinx.coroutines.delay(100)

                    val analysisMetadata = AnalysisMetadata(
                        totalMessagesProcessed = allTransactions.size,
                        lastProcessedMessageId = allTransactions.maxOfOrNull { it.date.time } ?: 0,
                        lastProcessedTimestamp = System.currentTimeMillis()
                    )
                    database.smsAnalysisCacheDao().insertAnalysisMetadata(analysisMetadata)

                    progressValue = 90
                    progressMessage = "Finalizing analysis..."
                    kotlinx.coroutines.delay(300)

                    // Check if there are new messages since last analysis
                    val lastAnalysis = database.smsAnalysisCacheDao().getLastAnalysisMetadata()
                    if (lastAnalysis != null && allTransactions.any { it.date.time > lastAnalysis.lastProcessedMessageId }) {
                        hasUnsavedChanges = true
                    }

                    // Step 8: Extract vendors from transactions
                    progressValue = 80
                    progressMessage = "Extracting vendors from transactions..."
                    kotlinx.coroutines.delay(100)

                    val vendors = vendorExtractor.extractVendorsFromTransactions(transactions)
                    if (vendors.isNotEmpty()) {
                        database.vendorDao().deleteAllVendors()
                        database.vendorDao().insertVendors(vendors)
                    }

                    progressValue = 100
                    progressMessage = "Analysis complete! Found ${transactions.size} transactions and ${vendors.size} vendors"
                }

            } catch (e: Exception) {
                progressMessage = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                kotlinx.coroutines.delay(1000) // Show completion message briefly
                isLoading = false
            }
        }
    }

    private fun isTransactionExcluded(transaction: Transaction, excludedIds: List<Long>): Boolean {
        // Simple exclusion logic based on transaction content
        // In a more sophisticated implementation, we would have transaction IDs
        val transactionSignature = "${transaction.description}_${transaction.amount}_${transaction.date.time}"
        val signatureHash = transactionSignature.hashCode().toLong()

        return excludedIds.contains(signatureHash)
    }

    private fun shouldUseCachedData(lastAnalysis: AnalysisMetadata?, cachedTransactions: List<SMSAnalysisCache>): Boolean {
        if (lastAnalysis == null || cachedTransactions.isEmpty()) return false

        // Use cache if analysis was done within the last hour
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        return lastAnalysis.lastAnalysisDate > oneHourAgo
    }

    private suspend fun updateCacheWithTransactions(transactions: List<Transaction>) {
        val cacheEntries = transactions.map { transaction ->
            SMSAnalysisCache(
                messageId = transaction.date.time, // Using timestamp as unique ID
                messageBody = transaction.description,
                sender = transaction.sender ?: "Unknown",
                timestamp = transaction.date.time,
                hasTransaction = true,
                transactionAmount = transaction.amount,
                transactionType = transaction.type,
                isExcluded = false
            )
        }

        database.smsAnalysisCacheDao().insertCachedMessages(cacheEntries)
    }

    @Composable
    fun MainScreen() {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "dashboard") {
            composable("dashboard") {
                DashboardScreen(navController)
            }
            composable("excluded_messages") {
                ExcludedMessagesScreen(navController)
            }
            composable("credit_summaries") {
                CreditSummariesScreen(navController)
            }
            composable("category_rules") {
                CategoryRulesScreen()
            }
            composable("message_browser") {
                MessageBrowserScreen()
            }
            composable("vendor_management") {
                VendorManagementScreen()
            }
            composable("transaction_sms_view") {
                TransactionSMSViewScreen()
            }
            composable("vendor_group_management") {
                VendorGroupManagementScreen()
            }
            composable("group_spending_overview") {
                GroupSpendingOverviewScreen()
            }
        }
    }

    @Composable
    fun DashboardScreen(navController: androidx.navigation.NavController) {
        val scrollState = rememberScrollState()
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getString(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sync button - only show when there are changes
                    if (hasUnsavedChanges) {
                        IconButton(
                            onClick = { syncChanges() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("🔄", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    IconButton(onClick = { navController.navigate("excluded_messages") }) {
                        Text("🚫")
                    }
                    IconButton(onClick = { navController.navigate("credit_summaries") }) {
                        Text("💰")
                    }
                    IconButton(onClick = { navController.navigate("message_browser") }) {
                        Text("💬")
                    }
                    IconButton(onClick = { navController.navigate("category_rules") }) {
                        Text("⚙️")
                    }
                    IconButton(onClick = { navController.navigate("vendor_management") }) {
                        Text("🏪")
                    }
                    IconButton(onClick = { navController.navigate("transaction_sms_view") }) {
                        Text("📱")
                    }
                    IconButton(onClick = { navController.navigate("vendor_group_management") }) {
                        Text("👥")
                    }
                    IconButton(onClick = { navController.navigate("group_spending_overview") }) {
                        Text("📊")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                PermissionRequestCard()
            } else if (isLoading) {
                LoadingCard()
            } else {
                SpendingOverviewCard()
                ExportOptionsCard()
                DailySpendingList()
                MonthlySpendingList()
            }
        }
    }

    @Composable
    fun PermissionRequestCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getString(R.string.permission_required),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = getString(R.string.grant_permission),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { requestSMSPermission() }) {
                    Text("Grant Permission")
                }
            }
        }
    }

    @Composable
    fun LoadingCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Linear progress indicator
                LinearProgressIndicator(
                    progress = progressValue / 100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress percentage
                Text(
                    text = "$progressValue% Complete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress message with better text handling
                Text(
                    text = progressMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Circular indicator for visual feedback
                CircularProgressIndicator()

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Please wait while we analyze your SMS messages...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun SpendingOverviewCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = getString(R.string.total_spending),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val totalSpending = calculator.calculateTotalSpending(transactions)
                val avgDaily = calculator.getAverageDailySpending(transactions)
                val avgMonthly = calculator.getAverageMonthlySpending(transactions)

                Text("Total: ₹${String.format("%.2f", totalSpending)}")
                Text("Daily Average: ₹${String.format("%.2f", avgDaily)}")
                Text("Monthly Average: ₹${String.format("%.2f", avgMonthly)}")
                Text("Transactions Found: ${transactions.size}")
            }
        }
    }

    @Composable
    fun ExportOptionsCard() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = getString(R.string.export_data),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { exportData(ExportFormat.CSV, CompressionType.NONE) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(getString(R.string.export_csv))
                    }

                    Button(
                        onClick = { exportData(ExportFormat.JSON, CompressionType.GZIP) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(getString(R.string.export_json))
                    }

                    Button(
                        onClick = { exportData(ExportFormat.PROTOBUF, CompressionType.GZIP) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(getString(R.string.export_protobuf))
                    }
                }
            }
        }
    }

    @Composable
    fun DailySpendingList() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getString(R.string.daily_spending),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Sort options
                    Row {
                        TextButton(
                            onClick = { dailySortOption = SortOption.AMOUNT_DESC },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (dailySortOption == SortOption.AMOUNT_DESC)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Most Spend", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(" | ", style = MaterialTheme.typography.labelSmall)
                        TextButton(
                            onClick = { dailySortOption = SortOption.DATE_ASC },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (dailySortOption == SortOption.DATE_ASC)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Oldest", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (dailySummaries.isEmpty()) {
                    Text(getString(R.string.no_transactions))
                } else {
                    val sortedDailySummaries = when (dailySortOption) {
                        SortOption.AMOUNT_DESC -> dailySummaries.sortedByDescending { it.totalSpent }
                        SortOption.DATE_ASC -> dailySummaries.sortedBy { it.date }
                        SortOption.DATE_DESC -> dailySummaries.sortedByDescending { it.date }
                    }

                    LazyColumn(
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(sortedDailySummaries) { summary ->
                            DailySpendingItem(summary)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MonthlySpendingList() {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getString(R.string.monthly_spending),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Sort options
                    Row {
                        TextButton(
                            onClick = { monthlySortOption = SortOption.AMOUNT_DESC },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (monthlySortOption == SortOption.AMOUNT_DESC)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Most Spend", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(" | ", style = MaterialTheme.typography.labelSmall)
                        TextButton(
                            onClick = { monthlySortOption = SortOption.DATE_ASC },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = if (monthlySortOption == SortOption.DATE_ASC)
                                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Oldest", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (monthlySummaries.isEmpty()) {
                    Text(getString(R.string.no_transactions))
                } else {
                    val sortedMonthlySummaries = when (monthlySortOption) {
                        SortOption.AMOUNT_DESC -> monthlySummaries.sortedByDescending { it.totalSpent }
                        SortOption.DATE_ASC -> monthlySummaries.sortedBy {
                            // Parse month string to date for sorting
                            try {
                                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(it.month) ?: Date()
                            } catch (e: Exception) {
                                Date()
                            }
                        }
                        SortOption.DATE_DESC -> monthlySummaries.sortedByDescending {
                            try {
                                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).parse(it.month) ?: Date()
                            } catch (e: Exception) {
                                Date()
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.height(300.dp)
                    ) {
                        items(sortedMonthlySummaries) { summary ->
                            MonthlySpendingItem(summary)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DailySpendingItem(summary: DailySummary) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        var showDetails by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetails = !showDetails }
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dateFormat.format(summary.date),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "₹${String.format("%.2f", summary.totalSpent)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showDetails && summary.transactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Transactions (${summary.transactions.size}):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.height(150.dp)
                ) {
                    items(summary.transactions) { transaction ->
                        TransactionDetailItem(transaction)
                    }
                }
            }

            Divider(modifier = Modifier.padding(top = 8.dp))
        }
    }

    @Composable
    fun MonthlySpendingItem(summary: MonthlySummary) {
        var showDetails by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDetails = !showDetails }
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    summary.month,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "₹${String.format("%.2f", summary.totalSpent)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showDetails && summary.dailySummaries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Daily Breakdown (${summary.dailySummaries.size} days):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    items(summary.dailySummaries) { dailySummary ->
                        DailySummaryDetailItem(dailySummary)
                    }
                }
            }

            Divider(modifier = Modifier.padding(top = 8.dp))
        }
    }

    @Composable
    fun TransactionDetailItem(transaction: Transaction) {
        var showExcludeDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            transaction.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(transaction.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "₹${String.format("%.2f", transaction.amount)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (transaction.type == com.smsanalytics.smstransactionanalyzer.model.TransactionType.DEBIT)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )

                        TextButton(
                            onClick = { showExcludeDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Exclude",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        if (showExcludeDialog) {
            AlertDialog(
                onDismissRequest = { showExcludeDialog = false },
                title = { Text("Exclude Transaction") },
                text = {
                    Text("Are you sure you want to exclude this transaction from analysis? This will prevent it from being counted in future spending calculations.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            excludeTransaction(transaction)
                            showExcludeDialog = false
                        }
                    ) {
                        Text("Exclude")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExcludeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    private fun excludeTransaction(transaction: Transaction) {
        lifecycleScope.launch {
            try {
                // Create a signature for the transaction to identify it
                val transactionSignature = "${transaction.description}_${transaction.amount}_${transaction.date.time}"
                val signatureHash = transactionSignature.hashCode().toLong()

                // Add to excluded messages
                val excludedMessage = ExcludedMessage(
                    messageId = signatureHash,
                    body = transaction.description,
                    sender = transaction.sender ?: "Unknown",
                    timestamp = transaction.date.time
                )

                database.excludedMessageDao().insertExcludedMessage(excludedMessage)

                // Also mark as excluded in cache if it exists
                database.smsAnalysisCacheDao().markMessageAsExcluded(transaction.date.time)

                // Remove transaction from local state instead of reloading all data
                transactions = transactions.filter { it != transaction }

                // Recalculate summaries based on updated transaction list
                dailySummaries = calculator.calculateDailySpending(transactions)
                monthlySummaries = calculator.calculateMonthlySpending(transactions)

                // Mark that there are unsaved changes
                hasUnsavedChanges = true

                Toast.makeText(this@MainActivity, "Transaction excluded successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error excluding transaction: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    fun DailySummaryDetailItem(dailySummary: DailySummary) {
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
        var showTransactions by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable { showTransactions = !showTransactions },
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        dateFormat.format(dailySummary.date),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "₹${String.format("%.2f", dailySummary.totalSpent)} (${dailySummary.transactionCount} txns)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (showTransactions && dailySummary.transactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Transactions:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.height(150.dp)
                    ) {
                        items(dailySummary.transactions) { transaction ->
                            TransactionDetailItem(transaction)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CreditSummariesScreen(navController: androidx.navigation.NavController) {
        val allTransactions by remember { mutableStateOf(transactions) }
        val creditSummaries by remember {
            mutableStateOf(calculator.calculateCreditSummaries(allTransactions))
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
                    text = "Credit Summaries",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { navController.navigate("dashboard") }) {
                    Text("←")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Credit overview card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Credit Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val totalCredits = calculator.calculateTotalCredits(allTransactions)
                    val avgDailyCredits = if (creditSummaries.isNotEmpty()) {
                        totalCredits / creditSummaries.size
                    } else 0.0

                    Text("Total Credits: ₹${String.format("%.2f", totalCredits)}")
                    Text("Daily Average: ₹${String.format("%.2f", avgDailyCredits)}")
                    Text("Credit Days: ${creditSummaries.size}")
                }
            }

            if (creditSummaries.isEmpty()) {
                Text(
                    text = "No credit transactions found",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(creditSummaries) { summary ->
                        CreditSummaryItem(summary)
                    }
                }
            }
        }
    }

    @Composable
    fun CreditSummaryItem(summary: DailySummary) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        var showDetails by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .clickable { showDetails = !showDetails },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dateFormat.format(summary.date),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "₹${String.format("%.2f", summary.totalSpent)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (showDetails && summary.transactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Credit Transactions (${summary.transactions.size}):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.height(120.dp)
                    ) {
                        items(summary.transactions) { transaction ->
                            TransactionDetailItem(transaction)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ExcludedMessagesScreen(navController: androidx.navigation.NavController) {
        var excludedMessages by remember { mutableStateOf<List<ExcludedMessage>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        // Load excluded messages when screen is displayed
        LaunchedEffect(Unit) {
            try {
                val messages = database.excludedMessageDao().getAllExcludedMessages()
                messages.collect { messageList ->
                    excludedMessages = messageList
                    isLoading = false
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading excluded messages", Toast.LENGTH_SHORT).show()
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
                        ExcludedMessageItem(excludedMessage)
                    }
                }
            }
        }
    }

    @Composable
    fun ExcludedMessageItem(excludedMessage: ExcludedMessage) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = excludedMessage.body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
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
                        onClick = { restoreExcludedMessage(excludedMessage) },
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

    private fun restoreExcludedMessage(excludedMessage: ExcludedMessage) {
        lifecycleScope.launch {
            try {
                // Remove from excluded messages
                database.excludedMessageDao().deleteExcludedMessage(excludedMessage)

                // If it exists in cache, mark as not excluded
                database.smsAnalysisCacheDao().markMessageAsIncluded(excludedMessage.messageId)

                // Reload data to reflect the restoration
                loadTransactionData()

                Toast.makeText(this@MainActivity, "Message restored to analysis", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error restoring message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncChanges() {
        lifecycleScope.launch {
            try {
                // Update the analysis metadata to reflect current state
                val analysisMetadata = AnalysisMetadata(
                    totalMessagesProcessed = transactions.size,
                    lastProcessedMessageId = transactions.maxOfOrNull { it.date.time } ?: 0,
                    lastProcessedTimestamp = System.currentTimeMillis()
                )
                database.smsAnalysisCacheDao().insertAnalysisMetadata(analysisMetadata)

                // Update cache with current transactions
                updateCacheWithTransactions(transactions)

                // Reset the unsaved changes flag
                hasUnsavedChanges = false

                Toast.makeText(this@MainActivity, "Changes synced successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error syncing changes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    fun VendorManagementScreen() {
        val navController = rememberNavController()
        var vendors by remember { mutableStateOf<List<com.smsanalytics.smstransactionanalyzer.model.Vendor>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var searchQuery by remember { mutableStateOf("") }

        // Load vendors when screen is displayed
        LaunchedEffect(Unit) {
            try {
                database.vendorDao().getAllVendors().collect { vendorList ->
                    vendors = vendorList
                    isLoading = false
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading vendors", Toast.LENGTH_SHORT).show()
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
                    text = "Vendor Management",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { navController.navigate("dashboard") }) {
                    Text("←")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search vendors") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Text("🔍") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (vendors.isEmpty()) {
                Text(
                    text = "No vendors found. Run SMS analysis first.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                val filteredVendors = if (searchQuery.isBlank()) {
                    vendors
                } else {
                    vendors.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }

                Text(
                    text = "Found ${filteredVendors.size} vendors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredVendors) { vendor ->
                        VendorItem(vendor)
                    }
                }
            }
        }
    }

    @Composable
    fun VendorItem(vendor: com.smsanalytics.smstransactionanalyzer.model.Vendor) {
        var showExcludeDialog by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vendor.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "₹${String.format("%.2f", vendor.totalSpent)} • ${vendor.transactionCount} transactions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (vendor.lastTransactionDate != null) {
                            Text(
                                text = "Last: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(vendor.lastTransactionDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        if (vendor.isExcluded) {
                            Text(
                                text = "EXCLUDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        TextButton(
                            onClick = { showExcludeDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (vendor.isExcluded) "Include" else "Exclude",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (vendor.isExcluded)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        if (showExcludeDialog) {
            AlertDialog(
                onDismissRequest = { showExcludeDialog = false },
                title = { Text(if (vendor.isExcluded) "Include Vendor" else "Exclude Vendor") },
                text = {
                    Text(if (vendor.isExcluded)
                        "Are you sure you want to include this vendor in analysis?"
                    else
                        "Are you sure you want to exclude this vendor from analysis? This will hide all transactions from this vendor."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            toggleVendorExclusion(vendor)
                            showExcludeDialog = false
                        }
                    ) {
                        Text(if (vendor.isExcluded) "Include" else "Exclude")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExcludeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    private fun toggleVendorExclusion(vendor: com.smsanalytics.smstransactionanalyzer.model.Vendor) {
        lifecycleScope.launch {
            try {
                database.vendorDao().updateVendorExclusion(vendor.id, !vendor.isExcluded)
                hasUnsavedChanges = true
                Toast.makeText(
                    this@MainActivity,
                    "Vendor ${if (vendor.isExcluded) "included" else "excluded"} successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error updating vendor: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    fun TransactionSMSViewScreen() {
        val navController = rememberNavController()

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
                    text = "Transaction SMS View",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { navController.navigate("dashboard") }) {
                    Text("←")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (transactions.isEmpty()) {
                Text(
                    text = "No transactions found. Run SMS analysis first.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                Text(
                    text = "Showing ${transactions.size} transaction SMS messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(transactions) { transaction ->
                        TransactionSMSItem(transaction)
                    }
                }
            }
        }
    }

    @Composable
    fun TransactionSMSItem(transaction: Transaction) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Amount badge
                        Surface(
                            color = if (transaction.type == com.smsanalytics.smstransactionanalyzer.model.TransactionType.DEBIT)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "₹${String.format("%.2f", transaction.amount)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (transaction.type == com.smsanalytics.smstransactionanalyzer.model.TransactionType.DEBIT)
                                    MaterialTheme.colorScheme.onErrorContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = transaction.smsBody,
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "From: ${transaction.sender ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(transaction.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun VendorGroupManagementScreen() {
        val navController = rememberNavController()
        var vendorGroups by remember { mutableStateOf<List<com.smsanalytics.smstransactionanalyzer.model.VendorGroup>>(emptyList()) }
        var vendors by remember { mutableStateOf<List<com.smsanalytics.smstransactionanalyzer.model.Vendor>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }
        var showCreateGroupDialog by remember { mutableStateOf(false) }
        var newGroupName by remember { mutableStateOf("") }

        // Load data when screen is displayed
        LaunchedEffect(Unit) {
            try {
                database.vendorGroupDao().getAllVendorGroups().collect { groups ->
                    vendorGroups = groups
                }
                database.vendorDao().getActiveVendors().collect { vendorList ->
                    vendors = vendorList
                    isLoading = false
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading data", Toast.LENGTH_SHORT).show()
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
                    text = "Vendor Groups",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { showCreateGroupDialog = true }) {
                        Text("➕")
                    }
                    IconButton(onClick = { navController.navigate("dashboard") }) {
                        Text("←")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (vendorGroups.isEmpty()) {
                Text(
                    text = "No vendor groups created yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(vendorGroups) { group ->
                        VendorGroupItem(group, vendors)
                    }
                }
            }
        }

        if (showCreateGroupDialog) {
            AlertDialog(
                onDismissRequest = { showCreateGroupDialog = false },
                title = { Text("Create New Group") },
                text = {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Group Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newGroupName.isNotBlank()) {
                                createVendorGroup(newGroupName)
                                newGroupName = ""
                                showCreateGroupDialog = false
                            }
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateGroupDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun VendorGroupItem(group: com.smsanalytics.smstransactionanalyzer.model.VendorGroup, allVendors: List<com.smsanalytics.smstransactionanalyzer.model.Vendor>) {
        var showAddVendorDialog by remember { mutableStateOf(false) }
        var selectedVendors by remember { mutableStateOf<List<com.smsanalytics.smstransactionanalyzer.model.Vendor>>(emptyList()) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Row {
                        IconButton(onClick = { showAddVendorDialog = true }) {
                            Text("➕", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { deleteVendorGroup(group) }) {
                            Text("🗑️", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Show vendors in this group
                val groupVendors = allVendors.filter { vendor ->
                    // In a real implementation, you'd check the VendorGroupMember table
                    // For now, we'll just show a placeholder
                    false
                }

                if (groupVendors.isEmpty()) {
                    Text(
                        text = "No vendors in this group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "${groupVendors.size} vendors",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showAddVendorDialog) {
            AlertDialog(
                onDismissRequest = { showAddVendorDialog = false },
                title = { Text("Add Vendors to ${group.name}") },
                text = {
                    Column {
                        Text("Select vendors to add to this group:")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.height(200.dp)
                        ) {
                            items(allVendors.filter { it.isExcluded == false }) { vendor ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (selectedVendors.contains(vendor)) {
                                                selectedVendors = selectedVendors - vendor
                                            } else {
                                                selectedVendors = selectedVendors + vendor
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedVendors.contains(vendor),
                                        onCheckedChange = null
                                    )
                                    Text(vendor.name, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            addVendorsToGroup(group, selectedVendors)
                            selectedVendors = emptyList()
                            showAddVendorDialog = false
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddVendorDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    @Composable
    fun GroupSpendingOverviewScreen() {
        val navController = rememberNavController()
        var groupSpending by remember { mutableStateOf<List<com.smsanalytics.smstransactionanalyzer.model.VendorGroupWithVendors>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        // Load group spending data
        LaunchedEffect(Unit) {
            try {
                val groups = database.vendorGroupDao().getAllVendorGroups()
                val spendingList = mutableListOf<com.smsanalytics.smstransactionanalyzer.model.VendorGroupWithVendors>()

                groups.collect { groupList ->
                    spendingList.clear()
                    for (group in groupList) {
                        database.vendorGroupDao().getVendorsByGroupId(group.id).collect { vendors ->
                            val totalSpent = vendors.sumOf { it.totalSpent }
                            spendingList.add(com.smsanalytics.smstransactionanalyzer.model.VendorGroupWithVendors(
                                group = group,
                                vendors = vendors,
                                totalSpent = totalSpent
                            ))
                        }
                    }
                    groupSpending = spendingList.toList()
                    isLoading = false
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading group spending", Toast.LENGTH_SHORT).show()
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
                    text = "Group Spending",
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
            } else if (groupSpending.isEmpty()) {
                Text(
                    text = "No vendor groups found. Create groups first.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                val totalGroupSpending = groupSpending.sumOf { it.totalSpent }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Total Group Spending",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "₹${String.format("%.2f", totalGroupSpending)}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(groupSpending) { groupData ->
                        GroupSpendingItem(groupData)
                    }
                }
            }
        }
    }

    @Composable
    fun GroupSpendingItem(groupData: com.smsanalytics.smstransactionanalyzer.model.VendorGroupWithVendors) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = groupData.group.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "₹${String.format("%.2f", groupData.totalSpent)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${groupData.vendors.size} vendors • ${groupData.vendors.sumOf { it.transactionCount }} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private fun createVendorGroup(name: String) {
        lifecycleScope.launch {
            try {
                val group = com.smsanalytics.smstransactionanalyzer.model.VendorGroup(name = name)
                database.vendorGroupDao().insertVendorGroup(group)
                hasUnsavedChanges = true
                Toast.makeText(this@MainActivity, "Group created successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error creating group: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteVendorGroup(group: com.smsanalytics.smstransactionanalyzer.model.VendorGroup) {
        lifecycleScope.launch {
            try {
                database.vendorGroupDao().deleteVendorGroup(group)
                hasUnsavedChanges = true
                Toast.makeText(this@MainActivity, "Group deleted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error deleting group: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addVendorsToGroup(group: com.smsanalytics.smstransactionanalyzer.model.VendorGroup, vendors: List<com.smsanalytics.smstransactionanalyzer.model.Vendor>) {
        lifecycleScope.launch {
            try {
                val members = vendors.map { vendor ->
                    com.smsanalytics.smstransactionanalyzer.model.VendorGroupMember(
                        groupId = group.id,
                        vendorId = vendor.id
                    )
                }
                database.vendorGroupDao().insertVendorGroupMembers(members)
                hasUnsavedChanges = true
                Toast.makeText(this@MainActivity, "Vendors added to group successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error adding vendors: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportData(format: ExportFormat, compression: CompressionType) {
        lifecycleScope.launch {
            isLoading = true
            progressValue = 0
            progressMessage = "Starting export process..."

            try {
                val result = exportManager.exportTransactions(transactions, format, compression) { progressVal, message ->
                    progressValue = progressVal
                    progressMessage = message
                }

                progressValue = 100
                progressMessage = "Export completed successfully!"

                // Show success message
                Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                progressMessage = "Export failed: ${e.message}"
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                kotlinx.coroutines.delay(2000) // Show completion message briefly
                isLoading = false
            }
        }
    }
}
