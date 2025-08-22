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
import com.smsanalytics.smstransactionanalyzer.model.YearlySummary
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.model.ExcludedMessage
import com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache
import com.smsanalytics.smstransactionanalyzer.model.AnalysisMetadata
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import com.smsanalytics.smstransactionanalyzer.ui.theme.SMSAnalyticsAppTheme
import com.smsanalytics.smstransactionanalyzer.ui.CategoryRulesScreen
import com.smsanalytics.smstransactionanalyzer.ui.MessageBrowserScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.ExcludedMessagesScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.CreditSummariesScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.DashboardScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.SenderManagementScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.VendorManagementScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.VendorGroupManagementScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.GroupSpendingOverviewScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.RuleTestingScreen
import com.smsanalytics.smstransactionanalyzer.ui.screens.MessageDetailScreen
import com.smsanalytics.smstransactionanalyzer.database.SMSDatabase
import com.smsanalytics.smstransactionanalyzer.util.VendorExtractor
import com.smsanalytics.smstransactionanalyzer.util.SenderExtractor
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import android.content.SharedPreferences

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
    private lateinit var senderExtractor: SenderExtractor

    private var transactions by mutableStateOf<List<Transaction>>(emptyList())
    private var dailySummaries by mutableStateOf<List<DailySummary>>(emptyList())
    private var monthlySummaries by mutableStateOf<List<MonthlySummary>>(emptyList())
    private var yearlySummaries by mutableStateOf<List<YearlySummary>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var hasPermission by mutableStateOf(false)
    private var progressValue by mutableStateOf(0)
    private var progressMessage by mutableStateOf("")

    // Sort options
    private var dailySortOption by mutableStateOf(SortOption.DATE_DESC)
    private var monthlySortOption by mutableStateOf(SortOption.DATE_DESC)
    private var vendorSortOption by mutableStateOf(SortOption.DATE_DESC)
    private var smsSortOption by mutableStateOf(SortOption.DATE_DESC)
    private var smsFilterOption by mutableStateOf("ALL") // "ALL", "DEBIT", "CREDIT"
    private var hasUnsavedChanges by mutableStateOf(false)
    private lateinit var sharedPreferences: SharedPreferences

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
        senderExtractor = SenderExtractor()
        sharedPreferences = getSharedPreferences("sms_analytics_prefs", MODE_PRIVATE)

        // Set message browser as the default home screen
        setDefaultHomeScreenToMessageBrowser()

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
            // Start loading transaction data in background for dashboard
            loadTransactionData()
        }
    }

    private fun requestSMSPermission() {
        requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
    }

    private fun getPreferredHomeScreen(): String {
        return sharedPreferences.getString("home_screen", "message_browser") ?: "message_browser"
    }

    private fun setDefaultHomeScreenToMessageBrowser() {
        // Set message browser as the default home screen
        sharedPreferences.edit().putString("home_screen", "message_browser").apply()
    }

    private fun setPreferredHomeScreen(screen: String) {
        sharedPreferences.edit().putString("home_screen", screen).apply()
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

                val lastAnalysis = database.smsAnalysisCacheDao().getLastAnalysisMetadata()
                val cachedTransactions = database.smsAnalysisCacheDao().getCachedTransactions()
                val shouldUseCache = shouldUseCachedData(lastAnalysis, cachedTransactions)

                if (shouldUseCache && cachedTransactions.isNotEmpty()) {
                    // Check if there are newer messages since last analysis
                    val lastProcessedTimestamp = lastAnalysis?.lastProcessedTimestamp ?: 0
                    val currentTime = System.currentTimeMillis()

                    progressValue = 10
                    progressMessage = "Checking for new messages since last analysis..."

                    // Try incremental processing first
                    val newTransactions = try {
                        smsReader.readTransactionSMSIncremental(lastProcessedTimestamp) { progressVal, message ->
                            // Adjust progress to fit in the 10-20 range for incremental processing
                            progressValue = 10 + (progressVal * 0.1).toInt()
                            progressMessage = message
                        }
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Incremental processing failed, falling back to full processing", e)
                        progressValue = 20
                        progressMessage = "Incremental processing failed, falling back to full processing..."

                        // Fall back to full processing
                        smsReader.readTransactionSMS { progressVal, message ->
                            progressValue = progressVal
                            progressMessage = message
                        }
                    }

                    if (newTransactions.isNotEmpty()) {
                        // Determine if this was incremental or full processing based on result size
                        val isIncremental = newTransactions.size < 50 // Assume incremental if less than 50 new messages

                        if (isIncremental) {
                            progressValue = 20
                            progressMessage = "Found ${newTransactions.size} new transactions, merging with cache..."

                            // Convert cached transactions to Transaction objects
                            val cachedTransactionObjects = cachedTransactions.map { cache ->
                                Transaction(
                                    amount = cache.transactionAmount ?: 0.0,
                                    type = cache.transactionType ?: com.smsanalytics.smstransactionanalyzer.model.TransactionType.DEBIT,
                                    description = cache.messageBody,
                                    date = Date(cache.timestamp),
                                    smsBody = cache.messageBody,
                                    sender = cache.sender
                                )
                            }

                            // Merge new transactions with cached ones
                            val allTransactions = cachedTransactionObjects + newTransactions
                            transactions = allTransactions.distinctBy { "${it.description}_${it.amount}_${it.date.time}" }

                            progressValue = 30
                            progressMessage = "Updating cache with ${newTransactions.size} new transactions..."

                            // Update cache with new transactions
                            updateCacheWithTransactions(newTransactions)

                            // Update analysis metadata with new information
                            val latestTransaction = allTransactions.maxByOrNull { it.date }
                            val updatedAnalysisMetadata = lastAnalysis?.copy(
                                totalMessagesProcessed = allTransactions.size,
                                lastProcessedMessageId = latestTransaction?.date?.time ?: lastAnalysis.lastProcessedMessageId,
                                lastProcessedTimestamp = currentTime
                            ) ?: AnalysisMetadata(
                                totalMessagesProcessed = allTransactions.size,
                                lastProcessedMessageId = latestTransaction?.date?.time ?: 0,
                                lastProcessedTimestamp = currentTime
                            )
                            database.smsAnalysisCacheDao().insertAnalysisMetadata(updatedAnalysisMetadata)

                            progressValue = 40
                            progressMessage = "Recalculating summaries with new data..."
                        } else {
                            // This was full processing due to incremental failure
                            progressValue = 20
                            progressMessage = "Full processing completed, ${newTransactions.size} total transactions found..."

                            // Filter out excluded transactions
                            val excludedMessageIds = database.excludedMessageDao().getAllExcludedMessageIds()
                            transactions = newTransactions.filter { transaction ->
                                !isTransactionExcluded(transaction, excludedMessageIds)
                            }

                            progressValue = 30
                            progressMessage = "Updating cache with all transactions..."

                            updateCacheWithTransactions(newTransactions)

                            // Update analysis metadata
                            val latestTransaction = newTransactions.maxByOrNull { it.date }
                            val updatedAnalysisMetadata = AnalysisMetadata(
                                totalMessagesProcessed = newTransactions.size,
                                lastProcessedMessageId = latestTransaction?.date?.time ?: 0,
                                lastProcessedTimestamp = currentTime
                            )
                            database.smsAnalysisCacheDao().insertAnalysisMetadata(updatedAnalysisMetadata)

                            progressValue = 40
                            progressMessage = "Recalculating summaries..."

                            // Extract vendors and senders (same as full processing)
                            progressValue = 50
                            progressMessage = "Extracting vendors from transactions..."

                            val vendors = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                vendorExtractor.extractVendorsFromTransactions(transactions)
                            }

                            progressValue = 60
                            progressMessage = "Extracting senders from transactions..."

                            val senders = withContext(kotlinx.coroutines.Dispatchers.Default) {
                                senderExtractor.extractSendersFromTransactions(transactions)
                            }

                            if (vendors.isNotEmpty()) {
                                database.vendorDao().deleteAllVendors()
                                database.vendorDao().insertVendors(vendors)
                            }

                            if (senders.isNotEmpty()) {
                                database.senderDao().deleteAllSenders()
                                database.senderDao().insertSenders(senders)
                            }

                            progressValue = 80
                            progressMessage = "Finalizing analysis..."
                            kotlinx.coroutines.delay(100)
                        }

                    } else {
                        // No new transactions, use cached data as-is
                        progressValue = 20
                        progressMessage = "No new messages found, using cached data..."

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

                        progressValue = 40
                        progressMessage = "Loading cached data..."
                    }

                    // Calculate spending from merged data
                    progressValue = 50
                    progressMessage = "Calculating daily spending for ${transactions.size} transactions..."

                    dailySummaries = calculator.calculateDailySpending(transactions)

                    progressValue = 60
                    progressMessage = "Found ${dailySummaries.size} days with transactions"

                    // Step 6: Calculate monthly spending
                    progressValue = 70
                    progressMessage = "Calculating monthly spending from ${dailySummaries.size} daily summaries..."

                    monthlySummaries = calculator.calculateMonthlySpending(transactions)

                    // Step 7: Calculate yearly spending
                    progressValue = 80
                    progressMessage = "Calculating yearly spending from ${monthlySummaries.size} monthly summaries..."

                    Log.d("MainActivity", "Monthly summaries count: ${monthlySummaries.size}")
                    if (monthlySummaries.isNotEmpty()) {
                        Log.d("MainActivity", "Sample monthly summary: ${monthlySummaries.first().month} - ₹${monthlySummaries.first().totalSpent}")
                    }

                    yearlySummaries = calculateYearlySpending(monthlySummaries)

                    Log.d("MainActivity", "Yearly summaries count: ${yearlySummaries.size}")
                    if (yearlySummaries.isNotEmpty()) {
                        Log.d("MainActivity", "Sample yearly summary: ${yearlySummaries.first().year} - ₹${yearlySummaries.first().totalSpent}")
                    }

                    progressValue = 90
                    progressMessage = "Data processing complete!"

                    progressValue = 100
                    if (newTransactions.size < 50) {
                        progressMessage = "Analysis complete! Loaded ${transactions.size} transactions (${transactions.size - newTransactions.size} from cache + ${newTransactions.size} new)"
                    } else {
                        progressMessage = "Analysis complete! Processed ${transactions.size} transactions"
                    }

                } else {
                    // Process fresh SMS data
                    progressValue = 10
                    progressMessage = "No valid cache found, processing SMS messages..."

                    // Step 2: Read SMS with progress
                    val allTransactions = smsReader.readTransactionSMS { progressVal, message ->
                        progressValue = progressVal
                        progressMessage = message
                    }

                    // Step 3: Filter out excluded transactions
                    progressValue = 20
                    progressMessage = "Filtering excluded transactions..."

                    val excludedMessageIds = database.excludedMessageDao().getAllExcludedMessageIds()
                    transactions = allTransactions.filter { transaction ->
                        !isTransactionExcluded(transaction, excludedMessageIds)
                    }

                    // Step 4: Update cache with new data
                    progressValue = 30
                    progressMessage = "Updating cache..."

                    updateCacheWithTransactions(allTransactions)

                    // Step 5: Calculate spending
                    progressValue = 40
                    progressMessage = "Calculating daily spending for ${transactions.size} transactions..."

                    dailySummaries = calculator.calculateDailySpending(transactions)

                    progressValue = 50
                    progressMessage = "Found ${dailySummaries.size} days with transactions"

                    // Step 6: Calculate monthly spending
                    progressValue = 60
                    progressMessage = "Calculating monthly spending from ${dailySummaries.size} daily summaries..."

                    monthlySummaries = calculator.calculateMonthlySpending(transactions)

                    // Step 7: Calculate yearly spending
                    progressValue = 65
                    progressMessage = "Calculating yearly spending from ${monthlySummaries.size} monthly summaries..."

                    yearlySummaries = calculateYearlySpending(monthlySummaries)

                    progressValue = 70
                    progressMessage = "Completed analysis: ${dailySummaries.size} days, ${monthlySummaries.size} months"

                    // Step 7: Save analysis metadata
                    progressValue = 80
                    progressMessage = "Saving analysis metadata..."

                    val analysisMetadata = AnalysisMetadata(
                        totalMessagesProcessed = allTransactions.size,
                        lastProcessedMessageId = allTransactions.maxOfOrNull { it.date.time } ?: 0,
                        lastProcessedTimestamp = System.currentTimeMillis()
                    )
                    database.smsAnalysisCacheDao().insertAnalysisMetadata(analysisMetadata)

                    progressValue = 90
                    progressMessage = "Finalizing analysis..."

                    // Check if there are new messages since last analysis
                    val lastAnalysis = database.smsAnalysisCacheDao().getLastAnalysisMetadata()
                    if (lastAnalysis != null && allTransactions.any { it.date.time > lastAnalysis.lastProcessedMessageId }) {
                        hasUnsavedChanges = true
                    }

                    // Step 8: Extract vendors from transactions (in background)
                    progressValue = 80
                    progressMessage = "Extracting vendors from transactions..."

                    // Extract vendors in background to prevent UI freeze
                    val vendors = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        vendorExtractor.extractVendorsFromTransactions(transactions)
                    }

                    // Step 9: Extract senders from transactions
                    progressValue = 85
                    progressMessage = "Extracting senders from transactions..."

                    val senders = withContext(kotlinx.coroutines.Dispatchers.Default) {
                        senderExtractor.extractSendersFromTransactions(transactions)
                    }

                    if (vendors.isNotEmpty()) {
                        database.vendorDao().deleteAllVendors()
                        database.vendorDao().insertVendors(vendors)
                    }

                    if (senders.isNotEmpty()) {
                        database.senderDao().deleteAllSenders()
                        database.senderDao().insertSenders(senders)
                    }

                    progressValue = 100
                    progressMessage = "Analysis complete! Found ${transactions.size} transactions, ${vendors.size} vendors, and ${senders.size} senders"
                }

            } catch (e: Exception) {
                progressMessage = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                kotlinx.coroutines.delay(200) // Brief pause for UX
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

        // Always use cache if available - no expiry
        return true
    }

    private fun calculateYearlySpending(monthlySummaries: List<MonthlySummary>): List<YearlySummary> {
        Log.d("calculateYearlySpending", "Input monthly summaries count: ${monthlySummaries.size}")

        if (monthlySummaries.isEmpty()) {
            Log.d("calculateYearlySpending", "No monthly summaries to process")
            return emptyList()
        }

        val result = monthlySummaries.groupBy { summary ->
            // Extract year from month string (e.g., "December 2023" -> "2023")
            val year = summary.month.split(" ").last()
            Log.d("calculateYearlySpending", "Processing month: ${summary.month}, extracted year: $year, amount: ${summary.totalSpent}")
            year
        }.map { (year, months) ->
            val yearlyTotal = months.sumOf { it.totalSpent }
            val yearlyTransactionCount = months.sumOf { it.dailySummaries.sumOf { daily -> daily.transactionCount } }
            Log.d("calculateYearlySpending", "Year: $year, months count: ${months.size}, total spent: $yearlyTotal, transaction count: $yearlyTransactionCount")

            YearlySummary(
                year = year,
                totalSpent = yearlyTotal,
                monthlySummaries = months,
                transactionCount = yearlyTransactionCount
            )
        }.sortedByDescending { it.year }

        Log.d("calculateYearlySpending", "Final yearly summaries count: ${result.size}")
        result.forEach { yearly ->
            Log.d("calculateYearlySpending", "Year: ${yearly.year}, Total: ₹${yearly.totalSpent}, Transactions: ${yearly.transactionCount}")
        }

        return result
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
        val preferredHomeScreen = remember { getPreferredHomeScreen() }

        NavHost(navController = navController, startDestination = preferredHomeScreen) {
            composable("dashboard") {
                com.smsanalytics.smstransactionanalyzer.ui.screens.DashboardScreen(
                    navController = navController,
                    transactions = transactions,
                    dailySummaries = dailySummaries,
                    monthlySummaries = monthlySummaries,
                    yearlySummaries = yearlySummaries,
                    hasPermission = hasPermission,
                    isLoading = isLoading,
                    hasUnsavedChanges = hasUnsavedChanges,
                    onRequestPermission = { requestSMSPermission() },
                    onSyncChanges = { syncChanges() },
                    onExcludeTransaction = { /* TODO: Implement exclude transaction */ },
                    onExportData = { format, compression -> exportData(format, compression) }
                )
            }
            composable("excluded_messages") {
                ExcludedMessagesScreen(navController)
            }
            composable("credit_summaries") {
                com.smsanalytics.smstransactionanalyzer.ui.screens.CreditSummariesScreen(
                    navController = navController,
                    transactions = transactions,
                    calculator = calculator
                )
            }
            composable("category_rules") {
                CategoryRulesScreen()
            }
            composable("message_browser") {
                MessageBrowserScreen(navController)
            }
            composable("vendor_management") {
                VendorManagementScreen(
                    navController = navController,
                    transactions = transactions,
                    database = database
                )
            }
            composable("sender_management") {
                SenderManagementScreen(
                    navController = navController,
                    transactions = transactions,
                    database = database
                )
            }
            composable("vendor_sms_detail/{vendorName}") { backStackEntry ->
                val vendorName = backStackEntry.arguments?.getString("vendorName") ?: ""
                Log.d("Navigation", "Vendor SMS detail - vendorName: $vendorName")
                MessageBrowserScreen(
                    navController = navController,
                    filterMode = com.smsanalytics.smstransactionanalyzer.ui.SMSFilterMode.VENDOR_SPECIFIC,
                    filterValue = vendorName
                )
            }
            composable("sender_sms_detail/{senderName}") { backStackEntry ->
                val senderName = backStackEntry.arguments?.getString("senderName") ?: ""
                Log.d("Navigation", "Sender SMS detail - senderName: $senderName")
                MessageBrowserScreen(
                    navController = navController,
                    filterMode = com.smsanalytics.smstransactionanalyzer.ui.SMSFilterMode.SENDER_SPECIFIC,
                    filterValue = senderName
                )
            }
            composable("transaction_sms_view") {
                MessageBrowserScreen(
                    navController = navController,
                    filterMode = com.smsanalytics.smstransactionanalyzer.ui.SMSFilterMode.TRANSACTION_ONLY
                )
            }
            composable("sms_by_year/{year}") { backStackEntry ->
                val year = backStackEntry.arguments?.getString("year") ?: ""
                // TODO: Implement SMSByYearScreen - navigate to message browser with year filter
                MessageBrowserScreen(
                    navController = navController,
                    filterMode = com.smsanalytics.smstransactionanalyzer.ui.SMSFilterMode.YEAR_SPECIFIC,
                    filterValue = year
                )
            }
            composable("sms_by_month/{year}/{month}") { backStackEntry ->
                val year = backStackEntry.arguments?.getString("year") ?: ""
                val month = backStackEntry.arguments?.getString("month") ?: ""
                // TODO: Implement SMSByMonthScreen - navigate to message browser with month filter
                MessageBrowserScreen(
                    navController = navController,
                    filterMode = com.smsanalytics.smstransactionanalyzer.ui.SMSFilterMode.MONTH_SPECIFIC,
                    filterValue = "$year-$month"
                )
            }
            composable("sms_by_date/{year}/{month}/{day}") { backStackEntry ->
                val year = backStackEntry.arguments?.getString("year") ?: ""
                val month = backStackEntry.arguments?.getString("month") ?: ""
                val day = backStackEntry.arguments?.getString("day") ?: ""
                // TODO: Implement SMSByDateScreen - navigate to message browser with date filter
                MessageBrowserScreen(
                    navController = navController,
                    filterMode = com.smsanalytics.smstransactionanalyzer.ui.SMSFilterMode.DATE_SPECIFIC,
                    filterValue = "$year-$month-$day"
                )
            }
            composable("vendor_group_management") {
                VendorGroupManagementScreen(
                    navController = navController,
                    database = database
                )
            }
            composable("group_spending_overview") {
                GroupSpendingOverviewScreen(
                    navController = navController,
                    database = database
                )
            }
            composable("settings") {
                com.smsanalytics.smstransactionanalyzer.ui.screens.SettingsScreen(navController)
            }
            composable("data_inspection") {
                com.smsanalytics.smstransactionanalyzer.ui.screens.DataInspectionScreen(navController)
            }
            composable("rule_testing") {
                RuleTestingScreen(
                    navController = navController,
                    database = database,
                    transactions = transactions
                )
            }
            composable("all_messages") {
                MessageBrowserScreen(
                    navController = navController,
                    filterMode = com.smsanalytics.smstransactionanalyzer.ui.SMSFilterMode.ALL_MESSAGES
                )
            }
            composable("message_detail/{messageId}") { backStackEntry ->
                val messageId = backStackEntry.arguments?.getString("messageId")?.toLongOrNull()
                if (messageId != null) {
                    MessageDetailScreen(
                        navController = navController,
                        messageId = messageId,
                        smsReader = smsReader
                    )
                }
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








    private fun bulkExcludeTransactions(transactionsToExclude: List<Transaction>) {
        lifecycleScope.launch {
            try {
                val activeTransactions = transactionsToExclude.filter { transaction ->
                    // Check if transaction is not already excluded
                    val transactionSignature = "${transaction.description}_${transaction.amount}_${transaction.date.time}"
                    val signatureHash = transactionSignature.hashCode().toLong()
                    val excludedIds = database.excludedMessageDao().getAllExcludedMessageIds()
                    !excludedIds.contains(signatureHash)
                }

                if (activeTransactions.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No transactions to exclude", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Add all transactions to excluded list
                activeTransactions.forEach { transaction ->
                    val transactionSignature = "${transaction.description}_${transaction.amount}_${transaction.date.time}"
                    val signatureHash = transactionSignature.hashCode().toLong()

                    val excludedMessage = ExcludedMessage(
                        messageId = signatureHash,
                        body = transaction.description,
                        sender = transaction.sender ?: "Unknown",
                        timestamp = transaction.date.time
                    )

                    database.excludedMessageDao().insertExcludedMessage(excludedMessage)
                }

                hasUnsavedChanges = true
                Toast.makeText(
                    this@MainActivity,
                    "${activeTransactions.size} transactions excluded successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Refresh data
                loadTransactionData()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error bulk excluding transactions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bulkExcludeMessages(messagesToExclude: List<com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache>) {
        lifecycleScope.launch {
            try {
                val activeMessages = messagesToExclude.filter { message ->
                    val excludedIds = database.excludedMessageDao().getAllExcludedMessageIds()
                    !excludedIds.contains(message.messageId)
                }

                if (activeMessages.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No messages to exclude", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Add all messages to excluded list
                activeMessages.forEach { message ->
                    val excludedMessage = ExcludedMessage(
                        messageId = message.messageId,
                        body = message.messageBody,
                        sender = message.sender ?: "Unknown",
                        timestamp = message.timestamp
                    )

                    database.excludedMessageDao().insertExcludedMessage(excludedMessage)
                }

                hasUnsavedChanges = true
                Toast.makeText(
                    this@MainActivity,
                    "${activeMessages.size} messages excluded successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Refresh data
                loadTransactionData()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error bulk excluding messages: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun excludeMessage(message: com.smsanalytics.smstransactionanalyzer.model.SMSAnalysisCache) {
        lifecycleScope.launch {
            try {
                val excludedMessage = ExcludedMessage(
                    messageId = message.messageId,
                    body = message.messageBody,
                    sender = message.sender ?: "Unknown",
                    timestamp = message.timestamp
                )

                database.excludedMessageDao().insertExcludedMessage(excludedMessage)

                // Also mark as excluded in cache
                database.smsAnalysisCacheDao().markMessageAsExcluded(message.messageId)

                hasUnsavedChanges = true
                Toast.makeText(this@MainActivity, "Message excluded successfully", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error excluding message: ${e.message}", Toast.LENGTH_SHORT).show()
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
                kotlinx.coroutines.delay(300) // Brief pause for UX
                isLoading = false
            }
        }
    }

}
