package com.smsanalytics.smstransactionanalyzer

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import com.smsanalytics.smstransactionanalyzer.ui.theme.SMSAnalyticsAppTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {

    private lateinit var smsReader: SMSReader

    private var isLoading by mutableStateOf(false)
    private var progressValue by mutableStateOf(0)
    private var progressMessage by mutableStateOf("")
    private var hasSMSPermission by mutableStateOf(false)
    private var hasWritePermission by mutableStateOf(false)

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasSMSPermission = isGranted
    }

    private val writePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasWritePermission = isGranted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        smsReader = SMSReader(this)

        hasSMSPermission = smsReader.hasSMSPermission()
        hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

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

        if (!hasSMSPermission) {
            smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }

        if (!hasWritePermission) {
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    @Composable
    fun MainScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SMS Exporter",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (!hasSMSPermission || !hasWritePermission) {
                Text(
                    text = "Permissions required: SMS and Write Storage",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = {
                    if (!hasSMSPermission) smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                    if (!hasWritePermission) writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }) {
                    Text("Grant Permissions")
                }
            } else {
                Button(
                    onClick = { exportSMSData() },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(if (isLoading) "Exporting..." else "Export SMS Data as JSON")
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(32.dp))
                    LinearProgressIndicator(
                        progress = progressValue / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = progressMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }

    private fun exportSMSData() {
        lifecycleScope.launch {
            isLoading = true
            progressValue = 0
            progressMessage = "Reading SMS data..."

            try {
                val smsMessages = smsReader.readAllSMS()
                progressValue = 50
                progressMessage = "Found ${smsMessages.size} messages, creating JSON..."

                val jsonArray = JSONArray()
                smsMessages.forEach { sms ->
                    val jsonObject = JSONObject().apply {
                        put("id", sms.id)
                        put("body", sms.body)
                        put("sender", sms.sender)
                        put("address", sms.address)
                        put("timestamp", sms.timestamp)
                        put("date", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(sms.timestamp)))
                        put("type", sms.type)
                        put("read", sms.read)
                        put("threadId", sms.threadId)
                    }
                    jsonArray.put(jsonObject)
                }

                progressValue = 75
                progressMessage = "Saving to Downloads folder..."

                val jsonContent = jsonArray.toString(2)

                // Save using MediaStore
                val contentResolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "sms_data_${System.currentTimeMillis()}.json")
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                }

                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(jsonContent)
                        }
                    }

                    progressValue = 100
                    progressMessage = "Export completed successfully!"
                    Toast.makeText(this@MainActivity, "SMS data exported to Downloads folder", Toast.LENGTH_LONG).show()
                } else {
                    throw Exception("Failed to create file")
                }

            } catch (e: Exception) {
                progressMessage = "Error: ${e.message}"
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                kotlinx.coroutines.delay(2000) // Show success message
                isLoading = false
                progressValue = 0
                progressMessage = ""
            }
        }
    }
}