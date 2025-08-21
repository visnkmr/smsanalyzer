package com.smsanalytics.smstransactionanalyzer.ui

import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smsanalytics.smstransactionanalyzer.database.SMSDatabase
import com.smsanalytics.smstransactionanalyzer.manager.CategoryRuleManager
import com.smsanalytics.smstransactionanalyzer.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val database = remember { SMSDatabase.getInstance(context) }
    val categoryRuleManager = remember { CategoryRuleManager(database.categoryRuleDao()) }

    NavHost(navController = navController, startDestination = "rules_list") {
        composable("rules_list") {
            CategoryRulesListScreen(navController, categoryRuleManager)
        }
        composable("add_rule") {
            AddEditRuleScreen(navController, categoryRuleManager, null)
        }
        composable("edit_rule/{ruleId}") { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toLongOrNull()
            AddEditRuleScreen(navController, categoryRuleManager, ruleId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesListScreen(
    navController: NavController,
    categoryRuleManager: CategoryRuleManager
) {
    val context = LocalContext.current
    val rules by categoryRuleManager.getAllRules().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Category Rules",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            FloatingActionButton(onClick = { navController.navigate("add_rule") }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        }

        // Rules List
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No category rules yet",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create rules to automatically categorize SMS transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rules) { rule ->
                    CategoryRuleItem(
                        rule = rule,
                        onEdit = { navController.navigate("edit_rule/${rule.id}") },
                        onToggle = {
                            scope.launch {
                                categoryRuleManager.toggleRuleActive(rule.id, !rule.isActive)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                categoryRuleManager.deleteRule(rule)
                                Toast.makeText(context, "Rule deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRuleItem(
    rule: CategoryRule,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${getCategoryIcon(rule.category)} ${rule.category}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row {
                    Switch(
                        checked = rule.isActive,
                        onCheckedChange = { onToggle() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onEdit) {
                        Text("✏️")
                    }
                    IconButton(onClick = onDelete) {
                        Text("🗑️")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (rule.keywords.isNotEmpty()) {
                Text(
                    text = "Keywords: ${rule.keywords.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (rule.senderPatterns.isNotEmpty()) {
                Text(
                    text = "Sender Patterns: ${rule.senderPatterns.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val amountRange = buildString {
                if (rule.amountRangeMin != null) append("Min: ₹${rule.amountRangeMin}")
                if (rule.amountRangeMin != null && rule.amountRangeMax != null) append(", ")
                if (rule.amountRangeMax != null) append("Max: ₹${rule.amountRangeMax}")
            }
            if (amountRange.isNotEmpty()) {
                Text(
                    text = "Amount Range: $amountRange",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Priority: ${rule.priority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRuleScreen(
    navController: NavController,
    categoryRuleManager: CategoryRuleManager,
    ruleId: Long?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var senderPatterns by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var amountRangeMin by remember { mutableStateOf("") }
    var amountRangeMax by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("") }

    val categories by remember { mutableStateOf(getBuiltInCategories()) }

    // Load existing rule if editing
    LaunchedEffect(ruleId) {
        if (ruleId != null) {
            val existingRule = categoryRuleManager.getRuleById(ruleId)
            existingRule?.let { rule ->
                name = rule.name
                keywords = rule.keywords.joinToString(", ")
                senderPatterns = rule.senderPatterns.joinToString(", ")
                selectedCategory = rule.category
                amountRangeMin = rule.amountRangeMin?.toString() ?: ""
                amountRangeMax = rule.amountRangeMax?.toString() ?: ""
                priority = rule.priority.toString()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = if (ruleId == null) "Add Category Rule" else "Edit Category Rule",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Rule Name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = keywords,
            onValueChange = { keywords = it },
            label = { Text("Keywords (comma separated)") },
            placeholder = { Text("e.g., food, restaurant, cafe") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = senderPatterns,
            onValueChange = { senderPatterns = it },
            label = { Text("Sender Patterns (regex, comma separated)") },
            placeholder = { Text("e.g., .*SBI.*, .*HDFC.*") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Category dropdown
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text("${getCategoryIcon(category)} $category") },
                        onClick = {
                            selectedCategory = category
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = amountRangeMin,
                onValueChange = { amountRangeMin = it },
                label = { Text("Min Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = amountRangeMax,
                onValueChange = { amountRangeMax = it },
                label = { Text("Max Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = priority,
            onValueChange = { priority = it },
            label = { Text("Priority (higher = more important)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { navController.navigateUp() }
            ) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    if (validateInputs(name, selectedCategory)) {
                        scope.launch {
                            try {
                                val rule = CategoryRule(
                                    id = ruleId ?: 0,
                                    name = name,
                                    keywords = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    senderPatterns = senderPatterns.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                    amountRangeMin = amountRangeMin.toDoubleOrNull(),
                                    amountRangeMax = amountRangeMax.toDoubleOrNull(),
                                    category = selectedCategory,
                                    priority = priority.toIntOrNull() ?: 0
                                )

                                if (ruleId == null) {
                                    categoryRuleManager.addRule(rule)
                                    Toast.makeText(context, "Rule created successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    categoryRuleManager.updateRule(rule)
                                    Toast.makeText(context, "Rule updated successfully", Toast.LENGTH_SHORT).show()
                                }

                                navController.navigateUp()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error saving rule: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text(if (ruleId == null) "Create Rule" else "Update Rule")
            }
        }
    }
}

private fun validateInputs(name: String, category: String): Boolean {
    return name.isNotEmpty() && category.isNotEmpty()
}
