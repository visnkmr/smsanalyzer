package com.smsanalytics.smstransactionanalyzer.export

import android.content.Context
import android.os.Environment
import com.smsanalytics.smstransactionanalyzer.model.DailySummary
import com.smsanalytics.smstransactionanalyzer.model.MonthlySummary
import com.smsanalytics.smstransactionanalyzer.model.Transaction
import com.smsanalytics.smstransactionanalyzer.sms.SMSReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.GZIPOutputStream
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

enum class ExportFormat {
    CSV, JSON, PROTOBUF
}

enum class CompressionType {
    NONE, GZIP, ZIP, TAR_GZ
}

class ExportManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    suspend fun exportTransactions(
        transactions: List<Transaction>,
        format: ExportFormat,
        compression: CompressionType = CompressionType.NONE,
        onProgress: (progress: Int, message: String) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val filename = "transactions_$timestamp.${getFileExtension(format, compression)}"
        val file = File(getExportDirectory(), filename)

        try {
            onProgress(5, "Preparing export data...")
            onProgress(10, "Found ${transactions.size} transactions to export")
            onProgress(20, "Formatting data as ${format.name}...")

            // Simulate processing each transaction with progress updates
            val totalTransactions = transactions.size
            var processedTransactions = 0

            when (format) {
                ExportFormat.CSV -> {
                    val csvContent = StringBuilder()
                    csvContent.append("Date,Amount,Type,Description,Sender,SMS Body\n")

                    transactions.forEach { transaction ->
                        processedTransactions++
                        val progress = 25 + (processedTransactions * 40 / totalTransactions)

                        if (processedTransactions % 50 == 0 || processedTransactions <= 10 || processedTransactions == totalTransactions) {
                            onProgress(progress, "Exporting transaction $processedTransactions of $totalTransactions to CSV")
                        }

                        csvContent.append("${dateFormat.format(transaction.date)},")
                        csvContent.append("${transaction.amount},")
                        csvContent.append("${transaction.type},")
                        csvContent.append("\"${transaction.description.replace("\"", "\"\"")}\",")
                        csvContent.append("\"${transaction.sender.replace("\"", "\"\"")}\",")
                        csvContent.append("\"${transaction.smsBody.replace("\"", "\"\"")}\"\n")
                    }

                    writeWithCompression(csvContent.toString(), file, compression)
                }
                ExportFormat.JSON -> {
                    val jsonContent = StringBuilder()
                    jsonContent.append("[\n")

                    transactions.forEachIndexed { index, transaction ->
                        processedTransactions++
                        val progress = 25 + (processedTransactions * 40 / totalTransactions)

                        if (processedTransactions % 50 == 0 || processedTransactions <= 10 || processedTransactions == totalTransactions) {
                            onProgress(progress, "Exporting transaction $processedTransactions of $totalTransactions to JSON")
                        }

                        jsonContent.append("  {\n")
                        jsonContent.append("    \"date\": \"${dateFormat.format(transaction.date)}\",\n")
                        jsonContent.append("    \"amount\": ${transaction.amount},\n")
                        jsonContent.append("    \"type\": \"${transaction.type}\",\n")
                        jsonContent.append("    \"description\": \"${transaction.description.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n")
                        jsonContent.append("    \"sender\": \"${transaction.sender.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n")
                        jsonContent.append("    \"smsBody\": \"${transaction.smsBody.replace("\\", "\\\\").replace("\"", "\\\"")}\"\n")
                        jsonContent.append("  }")
                        if (index < transactions.size - 1) jsonContent.append(",")
                        jsonContent.append("\n")
                    }

                    jsonContent.append("]\n")
                    writeWithCompression(jsonContent.toString(), file, compression)
                }
                ExportFormat.PROTOBUF -> {
                    // For simplicity, using JSON-like format for protobuf
                    exportAsJSON(transactions, file, compression)
                }
            }

            onProgress(70, "Applying compression (${compression.name})...")
            onProgress(85, "Finalizing file creation...")
            onProgress(95, "Saving file to device...")
            onProgress(100, "Export completed successfully! $totalTransactions transactions exported")

            return@withContext "Export successful: ${file.absolutePath}"
        } catch (e: Exception) {
            onProgress(0, "Export failed: ${e.message}")
            return@withContext "Export failed: ${e.message}"
        }
    }

    suspend fun exportAllSMS(
        format: ExportFormat,
        compression: CompressionType = CompressionType.NONE
    ): String = withContext(Dispatchers.IO) {
        try {
            // Use cached data instead of reprocessing SMS
            val database = com.smsanalytics.smstransactionanalyzer.database.SMSDatabase.getInstance(context)
            val cachedTransactions = database.smsAnalysisCacheDao().getCachedTransactions()

            if (cachedTransactions.isEmpty()) {
                return@withContext "No SMS data available. Run SMS analysis first."
            }

            // Convert cached transactions to SMSMessage format
            val allSMS = cachedTransactions.map { cache ->
                com.smsanalytics.smstransactionanalyzer.sms.SMSReader.SMSMessage(
                    id = cache.messageId,
                    body = cache.messageBody,
                    sender = cache.sender,
                    timestamp = cache.timestamp,
                    type = 1 // Default to inbox
                )
            }

            val timestamp = System.currentTimeMillis()
            val filename = "all_sms_$timestamp.${getFileExtension(format, compression)}"
            val file = File(getExportDirectory(), filename)

            when (format) {
                ExportFormat.CSV -> exportSMSAsCSV(allSMS, file, compression)
                ExportFormat.JSON -> exportSMSAsJSON(allSMS, file, compression)
                ExportFormat.PROTOBUF -> exportSMSAsProtobuf(allSMS, file, compression)
            }
            return@withContext "Export successful: ${file.absolutePath}"
        } catch (e: Exception) {
            return@withContext "Export failed: ${e.message}"
        }
    }

    private fun exportAsCSV(transactions: List<Transaction>, file: File, compression: CompressionType) {
        val csvContent = StringBuilder()
        csvContent.append("Date,Amount,Type,Description,Sender,SMS Body\n")

        transactions.forEach { transaction ->
            csvContent.append("${dateFormat.format(transaction.date)},")
            csvContent.append("${transaction.amount},")
            csvContent.append("${transaction.type},")
            csvContent.append("\"${transaction.description.replace("\"", "\"\"")}\",")
            csvContent.append("\"${transaction.sender.replace("\"", "\"\"")}\",")
            csvContent.append("\"${transaction.smsBody.replace("\"", "\"\"")}\"\n")
        }

        writeWithCompression(csvContent.toString(), file, compression)
    }

    private fun exportSMSAsCSV(smsList: List<SMSReader.SMSMessage>, file: File, compression: CompressionType) {
        val csvContent = StringBuilder()
        csvContent.append("Date,Sender,Type,Message\n")

        smsList.forEach { sms ->
            val date = Date(sms.timestamp)
            val type = if (sms.type == 1) "Inbox" else "Sent"
            csvContent.append("${dateFormat.format(date)},")
            csvContent.append("\"${sms.sender.replace("\"", "\"\"")}\",")
            csvContent.append("$type,")
            csvContent.append("\"${sms.body.replace("\"", "\"\"")}\"\n")
        }

        writeWithCompression(csvContent.toString(), file, compression)
    }

    private fun exportAsJSON(transactions: List<Transaction>, file: File, compression: CompressionType) {
        val jsonContent = StringBuilder()
        jsonContent.append("[\n")

        transactions.forEachIndexed { index, transaction ->
            jsonContent.append("  {\n")
            jsonContent.append("    \"date\": \"${dateFormat.format(transaction.date)}\",\n")
            jsonContent.append("    \"amount\": ${transaction.amount},\n")
            jsonContent.append("    \"type\": \"${transaction.type}\",\n")
            jsonContent.append("    \"description\": \"${transaction.description.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n")
            jsonContent.append("    \"sender\": \"${transaction.sender.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n")
            jsonContent.append("    \"smsBody\": \"${transaction.smsBody.replace("\\", "\\\\").replace("\"", "\\\"")}\"\n")
            jsonContent.append("  }")
            if (index < transactions.size - 1) jsonContent.append(",")
            jsonContent.append("\n")
        }

        jsonContent.append("]\n")
        writeWithCompression(jsonContent.toString(), file, compression)
    }

    private fun exportSMSAsJSON(smsList: List<SMSReader.SMSMessage>, file: File, compression: CompressionType) {
        val jsonContent = StringBuilder()
        jsonContent.append("[\n")

        smsList.forEachIndexed { index, sms ->
            val date = Date(sms.timestamp)
            val type = if (sms.type == 1) "Inbox" else "Sent"
            jsonContent.append("  {\n")
            jsonContent.append("    \"date\": \"${dateFormat.format(date)}\",\n")
            jsonContent.append("    \"sender\": \"${sms.sender.replace("\\", "\\\\").replace("\"", "\\\"")}\",\n")
            jsonContent.append("    \"type\": \"$type\",\n")
            jsonContent.append("    \"message\": \"${sms.body.replace("\\", "\\\\").replace("\"", "\\\"")}\"\n")
            jsonContent.append("  }")
            if (index < smsList.size - 1) jsonContent.append(",")
            jsonContent.append("\n")
        }

        jsonContent.append("]\n")
        writeWithCompression(jsonContent.toString(), file, compression)
    }

    private fun exportAsProtobuf(transactions: List<Transaction>, file: File, compression: CompressionType) {
        // For simplicity, using JSON-like format for protobuf
        // In a real implementation, you would use actual protobuf serialization
        exportAsJSON(transactions, file, compression)
    }

    private fun exportSMSAsProtobuf(smsList: List<SMSReader.SMSMessage>, file: File, compression: CompressionType) {
        // For simplicity, using JSON-like format for protobuf
        exportSMSAsJSON(smsList, file, compression)
    }

    private fun writeWithCompression(content: String, file: File, compression: CompressionType) {
        when (compression) {
            CompressionType.NONE -> {
                FileOutputStream(file).use { it.write(content.toByteArray()) }
            }
            CompressionType.GZIP -> {
                GZIPOutputStream(FileOutputStream(file)).use { it.write(content.toByteArray()) }
            }
            CompressionType.ZIP -> {
                ZipOutputStream(FileOutputStream(file)).use { zipOut ->
                    zipOut.putNextEntry(ZipEntry("data.txt"))
                    zipOut.write(content.toByteArray())
                    zipOut.closeEntry()
                }
            }
            CompressionType.TAR_GZ -> {
                // Simplified TAR_GZ: just use GZIP since TAR creation is complex without external libraries
                GZIPOutputStream(FileOutputStream(file)).use { it.write(content.toByteArray()) }
            }
        }
    }

    private fun getFileExtension(format: ExportFormat, compression: CompressionType): String {
        val baseExtension = when (format) {
            ExportFormat.CSV -> "csv"
            ExportFormat.JSON -> "json"
            ExportFormat.PROTOBUF -> "pb"
        }

        return when (compression) {
            CompressionType.NONE -> baseExtension
            CompressionType.GZIP -> "$baseExtension.gz"
            CompressionType.ZIP -> "zip"
            CompressionType.TAR_GZ -> "tar.gz"
        }
    }

    private fun getExportDirectory(): File {
        val exportDir = File(context.getExternalFilesDir(null), "SMSAnalytics_Exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    fun getExportDirectoryPath(): String {
        return getExportDirectory().absolutePath
    }

    fun listExportedFiles(): List<File> {
        val exportDir = getExportDirectory()
        return if (exportDir.exists()) {
            exportDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }
}
