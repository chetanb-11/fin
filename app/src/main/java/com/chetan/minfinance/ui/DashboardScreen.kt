package com.chetan.minfinance.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

    // Observe Room data reactively via ViewModel stateflows
    val uncategorizedExpenses by viewModel.uncategorizedExpenses.collectAsStateWithLifecycle()
    val categorizedExpenses by viewModel.categorizedExpenses.collectAsStateWithLifecycle()

    // State for notification permission checking
    var isPermissionGranted by remember {
        mutableStateOf(SmsNotificationListenerService.isPermissionGranted(context))
    }
    var isAccessibilityGranted by remember {
        mutableStateOf(com.chetan.minfinance.service.PaytmAccessibilityService.isPermissionGranted(context))
    }

    // Checking system permission on lifecycle/resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = SmsNotificationListenerService.isPermissionGranted(context)
                isAccessibilityGranted = com.chetan.minfinance.service.PaytmAccessibilityService.isPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Input States for adding/simulating
    var showManualAddDialog by remember { mutableStateOf(false) }
    var mockSmsText by remember { mutableStateOf("") }
    val currencySymbol = "₹"
    val formatFormatter = remember {
        DecimalFormat("₹#,##0.00")
    }

    // Search filter for historical categorized items
    var searchHistoryQuery by remember { mutableStateOf("") }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

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
                    Column(modifier = Modifier.padding(end = 16.dp)) {
                        Text(
                            text = "Finbox",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-1.5).sp
                            )
                        )
                        Text(
                            text = "Clean on-device expense tracking",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                },
                actions = {
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
                text = { Text("Add Expense", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
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
                .padding(horizontal = 24.dp), // Spacious structural padding
            verticalArrangement = Arrangement.spacedBy(28.dp) // Beautiful editorial spacing scale
        ) {
            // Section 0: Permission Banners
            if (!isAccessibilityGranted || !isPermissionGranted) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        if (!isAccessibilityGranted) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Accessibility,
                                                contentDescription = "Accessibility Alert",
                                                tint = MaterialTheme.colorScheme.onSecondary
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Paytm Scraper Suspended",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                text = "Enable Paytm Accessibility Integration to capture Paytm payments instantly screen-to-app.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            context.startActivity(
                                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .testTag("grant_accessibility_permission_button")
                                    ) {
                                        Text("Configure Paytm Scraper", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (!isPermissionGranted) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
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
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .testTag("grant_permission_button")
                                    ) {
                                        Text("Configure Notification Sync", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 1: Dashboard Analytics Summary
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Total Outstanding card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "INBOX UNREAD",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${uncategorizedExpenses.size}",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-2.5).sp
                                ),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "items pending",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    // Total spent tracked
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        val totalSpent = remember(categorizedExpenses) {
                            categorizedExpenses.sumOf { if (it.isIncome) -it.amount else it.amount }
                        }
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "NET TRACED",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = formatFormatter.format(totalSpent),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-2.5).sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "all-time net balance",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "Inbox",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Inbox (Uncategorized)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    if (uncategorizedExpenses.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.scale(1.1f)
                        ) {
                            Text(
                                text = "${uncategorizedExpenses.size}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Section 3: Inbox (Uncategorized) Items with Swipe to Dismiss and animation
            if (uncategorizedExpenses.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Inbox Clear",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "All caught up!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Your inbox is perfectly clean. All transactions intercepted through Paytm, Google Pay, PhonePe, or simulated SMS have been processed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                items(
                    items = uncategorizedExpenses,
                    key = { it.id }
                ) { item ->
                    Box(modifier = Modifier.animateItem()) {
                        UncategorizedExpenseCard(
                            expense = item,
                            formatter = formatFormatter,
                            onCategorize = { cat -> viewModel.categorizeExpense(item, cat) },
                            onDelete = { viewModel.deleteExpense(item) },
                            onClick = { editingExpense = item }
                        )
                    }
                }
            }

            // Section 4: Live SMS Sandbox Panel for Easy Testing
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Sandbox",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
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
                                    label = { Text(preset, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
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
                            shape = RoundedCornerShape(10.dp),
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
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.testTag("simulate_button")
                            ) {
                                Text("Feed to Scanner", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Categorized Transactions",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                        }
                        if (filteredHistory.isNotEmpty()) {
                            Text(
                                text = "${filteredHistory.size} items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Search TextField with modern rounded borders
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
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // Section 6: History Items List with spring animations & sleek timeline ledger details
            if (filteredHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(20.dp)
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
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(
                    items = filteredHistory,
                    key = { it.id }
                ) { item ->
                    Box(modifier = Modifier.animateItem()) {
                        CategorizedExpenseCard(
                            expense = item,
                            formatter = formatFormatter,
                            onUncategorize = {
                                viewModel.categorizeExpense(item.copy(isCategorized = false, category = null), "")
                            },
                            onDelete = { viewModel.deleteExpense(item) },
                            onClick = { editingExpense = item }
                        )
                    }
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(96.dp))
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
            title = { Text("Log New Expense", fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        shape = RoundedCornerShape(10.dp),
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
                        shape = RoundedCornerShape(10.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("0.00") }
                    )

                    // Optional pre-categorize on manual creation styled elegantly
                    Column {
                        Text(
                            text = "Choose Category (Optional):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                                    label = { Text(cat.name, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = CircleShape,
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("dialog_confirm_button")
                ) {
                    Text("Save Expense", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualAddDialog = false },
                    modifier = Modifier.testTag("dialog_cancel_button")
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Modal Edit/Update/Delete Transaction Dialog
    if (editingExpense != null) {
        EditTransactionDialog(
            expense = editingExpense!!,
            currencySymbol = currencySymbol,
            onDismiss = { editingExpense = null },
            onSave = { updated -> viewModel.updateExpense(updated) },
            onDelete = { toDelete -> viewModel.deleteExpense(toDelete) }
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
    onClick: () -> Unit = {},
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
            val dragProgress = dismissState.progress
            val dragFraction = dragProgress.coerceIn(0f, 1f)
            val iconScale by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1.3f else 0.8f,
                label = "trash_scale"
            )
            // Beautiful smooth gradient transition into deep errorContainer tint
            val gradientBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = dragFraction * 0.95f)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(gradientBrush)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.scale(iconScale)
                )
            }
        },
        content = {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                        )
                    )
                ),
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onClick() }
                    .testTag("expense_item_card_${expense.id}")
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatTimestamp(expense.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = (if (expense.isIncome) "+ " else "") + formatter.format(expense.amount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (expense.isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ASSIGN CATEGORY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                letterSpacing = 1.sp
                            )
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
                                    label = { Text(cat.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = cat.color,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = cat.color.copy(alpha = 0.08f), // organic soft tonal alpha
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = CircleShape, // Organic seamless pill background
                                    border = null, // edge-to-edge organic feel
                                    modifier = Modifier
                                        .height(48.dp) // Comfortable touch target (minimum 48.dp)
                                        .testTag("category_chip_${cat.name}_${expense.id}")
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
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val categoryUi = getCategoryUiOf(expense.category)

    // Rendered as an elegant, vertical timeline list row rather than dense boxes
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min) // Critical for vertical timeline line fill
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("history_item_card_${expense.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Vertical Timeline Connector & Expressive Category Indicator
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            // Precise vertical timeline line
            VerticalDivider(
                modifier = Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .align(Alignment.Center),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Dynamic rounded category circle layered directly on the center of the timeline connector
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(categoryUi.color.copy(alpha = 0.12f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryUi.icon,
                    contentDescription = expense.category,
                    tint = categoryUi.color,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Right Block: Clean Text, high-contrast currency, and actions
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.merchantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTimestamp(expense.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                    // High-aesthetic tonal alpha category chip
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(categoryUi.color.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
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

            // High contrast currency value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val displayAmount = if (expense.isIncome) {
                    "+ " + formatter.format(expense.amount)
                } else {
                    formatter.format(expense.amount)
                }
                Text(
                    text = displayAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (expense.isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )

                // Uncategorize Action (Send back to Inbox)
                IconButton(
                    onClick = onUncategorize,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("uncategorize_button_${expense.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Move to Inbox",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Delete Expense permanently
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("delete_history_button_${expense.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete permanently",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    expense: Expense,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit,
    onDelete: (Expense) -> Unit
) {
    var merchantName by remember { mutableStateOf(expense.merchantName) }
    var amountInput by remember { mutableStateOf(expense.amount.toString()) }
    var isIncome by remember { mutableStateOf(expense.isIncome) }
    var selectedCategory by remember { mutableStateOf(expense.category) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Segmented Button Row / Switch to toggle between Expense and Income
                Column {
                    Text(
                        text = "Transaction Type",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Expense Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (!isIncome) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable { isIncome = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Expense",
                                fontWeight = FontWeight.Bold,
                                color = if (!isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Income Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isIncome) Color(0xFF2E7D32).copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable { isIncome = true }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Income",
                                fontWeight = FontWeight.Bold,
                                color = if (isIncome) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Merchant Name Field
                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text("Merchant / Source Name") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_merchant_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Amount Field
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount ($currencySymbol)") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_amount_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Category Chips Selector
                Column {
                    Text(
                        text = "Category (Optional)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                label = { Text(cat.name, fontWeight = FontWeight.SemiBold) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = CircleShape,
                                modifier = Modifier.testTag("edit_cat_chip_${cat.name}")
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Delete button inside dialog
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TextButton(
                        onClick = { 
                            onDelete(expense)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("dialog_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp).padding(end = 4.dp)
                        )
                        Text(
                            "Delete Transaction",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.trim().toDoubleOrNull()
                    if (merchantName.trim().isNotEmpty() && amount != null && amount > 0) {
                        onSave(
                            expense.copy(
                                merchantName = merchantName.trim(),
                                amount = amount,
                                isIncome = isIncome,
                                category = selectedCategory,
                                isCategorized = selectedCategory != null
                            )
                        )
                        onDismiss()
                    }
                },
                enabled = merchantName.trim().isNotEmpty() && amountInput.trim().toDoubleOrNull() != null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("edit_dialog_confirm_button")
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("edit_dialog_cancel_button")
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
