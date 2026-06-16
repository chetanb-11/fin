package com.chetan.minfinance.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chetan.minfinance.data.Expense
import com.chetan.minfinance.service.SmsNotificationListenerService
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// Category mapping helper
data class CategoryUi(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

val CATEGORIES = listOf(
    CategoryUi("Food", Icons.Default.Fastfood, Color(0xFFFF7043)),         // Coral Orange
    CategoryUi("Groceries", Icons.Default.ShoppingCart, Color(0xFF66BB6A)),    // Deep Green
    CategoryUi("Shopping", Icons.Default.LocalMall, Color(0xFFAB47BC)),        // Royal Purple
    CategoryUi("Transport", Icons.Default.DirectionsCar, Color(0xFFFFCA28)),   // Golden Amber
    CategoryUi("Bills", Icons.AutoMirrored.Filled.ReceiptLong, Color(0xFFEC407A)), // Rose Pink
    CategoryUi("Entertainment", Icons.Default.LocalActivity, Color(0xFF29B6F6)), // Cool Blue
    CategoryUi("Other", Icons.Default.Category, Color(0xFF78909C))            // Blue Gray
)

fun getCategoryUiOf(name: String?): CategoryUi {
    return CATEGORIES.find { it.name.equals(name, ignoreCase = true) } ?: CategoryUi(
        name ?: "Other",
        Icons.Default.Category,
        Color(0xFF78909C)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Observe Room data reactively via ViewModel stateflows
    val uncategorizedExpenses by viewModel.uncategorizedExpenses.collectAsStateWithLifecycle()
    val categorizedExpenses by viewModel.categorizedExpenses.collectAsStateWithLifecycle()

    // State for notification permission checking
    var isPermissionGranted by remember {
        mutableStateOf(SmsNotificationListenerService.isPermissionGranted(context))
    }

    // Checking system permission on lifecycle/resume
    DisposableEffect(Unit) {
        val observer = {
            isPermissionGranted = SmsNotificationListenerService.isPermissionGranted(context)
        }
        // Poll permission shortly on load/re-entry
        observer()
        onDispose {}
    }

    // Input States for adding/simulating
    var showManualAddDialog by remember { mutableStateOf(false) }
    var mockSmsText by remember { mutableStateOf("") }
    var usdCurrencyMode by remember { mutableStateOf(false) } // ₹ vs $ formatter toggle

    val currencySymbol = if (usdCurrencyMode) "$" else "₹"
    val formatFormatter = remember(usdCurrencyMode) {
        DecimalFormat(if (usdCurrencyMode) "$#,##0.00" else "₹#,##0.00")
    }

    // Search filter for historical categorized items
    var searchHistoryQuery by remember { mutableStateOf("") }

    val filteredHistory = remember(categorizedExpenses, searchHistoryQuery) {
        if (searchHistoryQuery.trim().isEmpty()) {
            categorizedExpenses
        } else {
            categorizedExpenses.filter {
                it.merchantName.contains(searchHistoryQuery, ignoreCase = true) ||
                (it.category?.contains(searchHistoryQuery, ignoreCase = true) == true)
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Finbox",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-1).sp
                            )
                        )
                        Text(
                            text = "Clean on-device expense tracking",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                },
                actions = {
                    // Currency Toggle Button
                    IconButton(
                        onClick = { usdCurrencyMode = !usdCurrencyMode },
                        modifier = Modifier.testTag("currency_toggle")
                    ) {
                        Text(
                            text = if (usdCurrencyMode) "₹" else "$",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Reset Database
                    IconButton(
                        onClick = { viewModel.clearAllData() },
                        modifier = Modifier.testTag("reset_database_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear all data",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showManualAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Expense") },
                text = { Text("Add Expense") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("fab_add_expense")
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 0: Permission Banner
            if (!isPermissionGranted) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Alert",
                                        tint = MaterialTheme.colorScheme.onError
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sync Interceptor Suspended",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "Grant Notification access to enable automatic Paytm/UPI intercept scans.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("grant_permission_button")
                            ) {
                                Text("Configure Access")
                            }
                        }
                    }
                }
            }

            // Section 1: Dashboard Analytics Summary
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Total Outstanding card
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "INBOX UNREAD",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${uncategorizedExpenses.size} items",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // Total spent tracked
                    ElevatedCard(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        val totalSpent = remember(categorizedExpenses) {
                            categorizedExpenses.sumOf { it.amount }
                        }
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "TOTAL CLASSIFIED",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatFormatter.format(totalSpent),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Section 2: Inbox (Uncategorized) Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "Inbox",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Inbox (Uncategorized)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (uncategorizedExpenses.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "${uncategorizedExpenses.size}",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Section 3: Inbox (Uncategorized) Items
            if (uncategorizedExpenses.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = "All Caught Up",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Unread Receipts Found",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Transactions intercepted through Paytm/SMS or manually entered will arrive here ready to categorize.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                items(
                    items = uncategorizedExpenses,
                    key = { it.id }
                ) { item ->
                    UncategorizedExpenseCard(
                        expense = item,
                        formatter = formatFormatter,
                        onCategorize = { cat -> viewModel.categorizeExpense(item, cat) },
                        onDelete = { viewModel.deleteExpense(item) }
                    )
                }
            }

            // Section 4: Live SMS Sandbox Panel for Easy Testing
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Sandbox",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Payment SMS Sandbox (Real-time Simulation)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "Trigger our regex matching engine manually to simulate intercepted payment push notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )

                        // Quick presets
                        Text(
                            text = "Quick Presets:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presets = listOf(
                                "Paid ₹120 to Swiggy via UPI",
                                "Successfully paid Rs 450 to Swiggy",
                                "Rs 3400 debited at Petrol Pump",
                                "Transaction at Netflix for Rs 299"
                            )
                            presets.forEach { preset ->
                                SuggestionChip(
                                    onClick = { mockSmsText = preset },
                                    label = { Text(preset, fontSize = 11.sp) },
                                    modifier = Modifier.testTag("sandbox_chip_$preset")
                                )
                            }
                        }

                        OutlinedTextField(
                            value = mockSmsText,
                            onValueChange = { mockSmsText = it },
                            placeholder = { Text("Enter mock payment SMS or check presets...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sandbox_sms_textfield"),
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    if (mockSmsText.trim().isNotEmpty()) {
                                        val succeeded = viewModel.simulateNotificationReceipt(mockSmsText)
                                        if (succeeded) {
                                            mockSmsText = ""
                                        }
                                    }
                                },
                                enabled = mockSmsText.trim().isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.testTag("simulate_button")
                            ) {
                                Text("Feed to Scanner", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Section 5: History Header with Search Box
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Categorized Transactions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (filteredHistory.isNotEmpty()) {
                            Text(
                                text = "${filteredHistory.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Search TextField
                    OutlinedTextField(
                        value = searchHistoryQuery,
                        onValueChange = { searchHistoryQuery = it },
                        placeholder = { Text("Filter by merchant or category...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchHistoryQuery.isNotEmpty()) {
                                IconButton(onClick = { searchHistoryQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_search_input"),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Section 6: History Items List
            if (filteredHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchHistoryQuery.trim().isEmpty()) {
                                "No history records found yet.\nCategories chosen from your inbox land here."
                            } else {
                                "No matches found for \"$searchHistoryQuery\"."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Normal,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = filteredHistory,
                    key = { it.id }
                ) { item ->
                    CategorizedExpenseCard(
                        expense = item,
                        formatter = formatFormatter,
                        onUncategorize = {
                            viewModel.categorizeExpense(item.copy(isCategorized = false, category = null), "")
                        },
                        onDelete = { viewModel.deleteExpense(item) }
                    )
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Modal Add Expense Dialog
    if (showManualAddDialog) {
        var merchantInput by remember { mutableStateOf("") }
        var amountInput by remember { mutableStateOf("") }
        var selectedCategory by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            title = { Text("Log New Expense", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = merchantInput,
                        onValueChange = { merchantInput = it },
                        label = { Text("Merchant / Store Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_merchant_input"),
                        singleLine = true,
                        placeholder = { Text("Exchange: Swiggy, Uber, Amazon") }
                    )

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Amount ($currencySymbol)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_amount_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("0.00") }
                    )

                    // Optional pre-categorize on manual creation
                    Column {
                        Text(
                            text = "Choose Category (Optional):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CATEGORIES.forEach { cat ->
                                val isSelected = selectedCategory == cat.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedCategory = if (isSelected) null else cat.name
                                    },
                                    label = { Text(cat.name) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("manual_cat_chip_${cat.name}")
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = amountInput.trim().toDoubleOrNull()
                        if (merchantInput.trim().isNotEmpty() && amount != null && amount > 0) {
                            viewModel.addManualExpense(
                                merchantName = merchantInput,
                                amount = amount,
                                category = selectedCategory
                            )
                            showManualAddDialog = false
                        }
                    },
                    enabled = merchantInput.trim().isNotEmpty() && amountInput.trim().toDoubleOrNull() != null,
                    modifier = Modifier.testTag("dialog_confirm_button")
                ) {
                    Text("Save Expense")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualAddDialog = false },
                    modifier = Modifier.testTag("dialog_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UncategorizedExpenseCard(
    expense: Expense,
    formatter: DecimalFormat,
    onCategorize: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { newValue ->
            if (newValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete() // Clear item on swipe-left
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false, // Drag left only
        backgroundContent = {
            val color = MaterialTheme.colorScheme.errorContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("expense_item_card_${expense.id}")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.merchantName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatTimestamp(expense.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        Text(
                            text = formatter.format(expense.amount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ASSIGN CATEGORY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CATEGORIES.forEach { cat ->
                                AssistChip(
                                    onClick = { onCategorize(cat.name) },
                                    label = { Text(cat.name, fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = cat.color,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = cat.color.copy(alpha = 0.12f),
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = null,
                                    modifier = Modifier.testTag("category_chip_${cat.name}_${expense.id}")
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun CategorizedExpenseCard(
    expense: Expense,
    formatter: DecimalFormat,
    onUncategorize: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryUi = getCategoryUiOf(expense.category)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("history_item_card_${expense.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left block (Icon & Text details)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryUi.color.copy(alpha = 0.15f))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryUi.icon,
                        contentDescription = expense.category,
                        tint = categoryUi.color
                    )
                }

                Column {
                    Text(
                        text = expense.merchantName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatTimestamp(expense.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(categoryUi.color.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = categoryUi.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = categoryUi.color,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Right block (Amount & Actions)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = formatter.format(expense.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Uncategorize Action (Send back to Inbox)
                IconButton(
                    onClick = onUncategorize,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("uncategorize_button_${expense.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Move to Inbox",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Expense permanently
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("delete_history_button_${expense.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete permanently",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
