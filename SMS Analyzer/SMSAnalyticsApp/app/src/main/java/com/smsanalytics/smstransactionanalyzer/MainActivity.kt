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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import com.smsanalytics.smstransactionanalyzer.ui.theme.SMSAnalyticsAppTheme
import com.smsanalytics.smstransactionanalyzer.ui.CategoryRulesScreen
import com.smsanalytics.smstransactionanalyzer.ui.MessageBrowserScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var smsReader: SMSReader
    private lateinit var calculator: SpendingCalculator
    private lateinit var exportManager: ExportManager

    private var transactions by mutableStateOf<List<Transaction>>(emptyList())
    private var dailySummaries by mutableStateOf<List<DailySummary>>(emptyList())
    private var monthlySummaries by mutableStateOf<List<MonthlySummary>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var hasPermission by mutableStateOf(false)
    private var progress by mutableStateOf(0)
    private var progressMessage by mutableStateOf("")

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
        progress = 0
        progressMessage = "Starting SMS analysis..."

        lifecycleScope.launch {
            try {
                // Step 1: Read SMS with progress
                transactions = smsReader.readTransactionSMS { progressValue, message ->
                    progress = progressValue
                    progressMessage = message
                }

                // Step 2: Calculate daily spending
                progress = 10
                progressMessage = "Calculating daily spending for ${transactions.size} transactions..."
                kotlinx.coroutines.delay(100)

                dailySummaries = calculator.calculateDailySpending(transactions)

                progress = 15
                progressMessage = "Found ${dailySummaries.size} days with transactions"
                kotlinx.coroutines.delay(100)

                // Step 3: Calculate monthly spending
                progress = 18
                progressMessage = "Calculating monthly spending from ${dailySummaries.size} daily summaries..."
                kotlinx.coroutines.delay(100)

                monthlySummaries = calculator.calculateMonthlySpending(transactions)

                progress = 25
                progressMessage = "Completed analysis: ${dailySummaries.size} days, ${monthlySummaries.size} months"
                kotlinx.coroutines.delay(100)

                // Step 4: Finalizing
                progress = 90
                progressMessage = "Finalizing analysis..."
                kotlinx.coroutines.delay(300) // Brief delay for UX

                progress = 100
                progressMessage = "Analysis complete! Found ${transactions.size} transactions"

            } catch (e: Exception) {
                progressMessage = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                kotlinx.coroutines.delay(1000) // Show completion message briefly
                isLoading = false
            }
        }
    }

    @Composable
    fun MainScreen() {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "dashboard") {
            composable("dashboard") {
                DashboardScreen(navController)
            }
            composable("category_rules") {
                CategoryRulesScreen()
            }
            composable("message_browser") {
                MessageBrowserScreen()
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
                Row {
                    IconButton(onClick = { navController.navigate("message_browser") }) {
                        Text("💬")
                    }
                    IconButton(onClick = { navController.navigate("category_rules") }) {
                        Text("⚙️")
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
                    progress = progress / 100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Progress percentage
                Text(
                    text = "$progress% Complete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress message
                Text(
                    text = progressMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
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
                Text(
                    text = getString(R.string.daily_spending),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (dailySummaries.isEmpty()) {
                    Text(getString(R.string.no_transactions))
                } else {
                    LazyColumn(
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(dailySummaries.take(7)) { summary ->
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
                Text(
                    text = getString(R.string.monthly_spending),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (monthlySummaries.isEmpty()) {
                    Text(getString(R.string.no_transactions))
                } else {
                    LazyColumn(
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(monthlySummaries.take(6)) { summary ->
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(dateFormat.format(summary.date))
            Text("₹${String.format("%.2f", summary.totalSpent)}")
        }
        Divider()
    }

    @Composable
    fun MonthlySpendingItem(summary: MonthlySummary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(summary.month)
            Text("₹${String.format("%.2f", summary.totalSpent)}")
        }
        Divider()
    }

    private fun exportData(format: ExportFormat, compression: CompressionType) {
        lifecycleScope.launch {
            isLoading = true
            progress = 0
            progressMessage = "Starting export process..."

            try {
                val result = exportManager.exportTransactions(transactions, format, compression) { progressValue, message ->
                    progress = progressValue
                    progressMessage = message
                }

                progress = 100
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
