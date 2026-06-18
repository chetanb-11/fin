package com.chetan.minfinance.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.atan2
import kotlin.math.sqrt
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

enum class FinboxTab(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    INBOX("inbox", Icons.Default.Inbox, "tab_inbox"),
    LEDGER("ledger", Icons.Default.History, "tab_ledger"),
    INSIGHTS("insights", Icons.Default.BarChart, "tab_insights"),
    CONTROL_CENTER("control center", Icons.Default.Settings, "tab_control_center")
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

    // Checking system permission on lifecycle/resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = SmsNotificationListenerService.isPermissionGranted(context)
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

    var currentTab by remember { mutableStateOf(FinboxTab.INBOX) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column(modifier = Modifier.padding(end = 16.dp)) {
                        val titleText = when (currentTab) {
                            FinboxTab.INBOX -> "Inbox"
                            FinboxTab.LEDGER -> "Ledger"
                            FinboxTab.INSIGHTS -> "Insights"
                            FinboxTab.CONTROL_CENTER -> "Control Center"
                        }
                        val subtitleText = when (currentTab) {
                            FinboxTab.INBOX -> "Verify and categorize incoming card alerts"
                            FinboxTab.LEDGER -> "Timeline of structured transactions"
                            FinboxTab.INSIGHTS -> "Spending analytics and month summary"
                            FinboxTab.CONTROL_CENTER -> "Settings, sync stats and sandbox"
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = (-1.5).sp
                            )
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                },
                actions = {
                    if (currentTab == FinboxTab.CONTROL_CENTER) {
                        IconButton(
                            onClick = { viewModel.clearAllData() },
                            modifier = Modifier.testTag("reset_database_top_shortcut")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear all data",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                // Return Enum values
                FinboxTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == FinboxTab.INBOX || currentTab == FinboxTab.LEDGER) {
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
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        when (currentTab) {
            FinboxTab.INBOX -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
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
                                    text = "Your inbox is perfectly clean. All credit card alerts intercepted from Gmail matching IndusInd Credit Card have been processed.",
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

                    item {
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
            FinboxTab.LEDGER -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
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

                    item {
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
            FinboxTab.INSIGHTS -> {
                val currentMonthCategorizedExpenses = remember(categorizedExpenses) {
                    val calendar = Calendar.getInstance()
                    val thisYear = calendar.get(Calendar.YEAR)
                    val thisMonth = calendar.get(Calendar.MONTH)
                    categorizedExpenses.filter { expense ->
                        val expCal = Calendar.getInstance().apply { timeInMillis = expense.timestamp }
                        expCal.get(Calendar.YEAR) == thisYear && expCal.get(Calendar.MONTH) == thisMonth
                    }
                }

                val currentMonthTotal = remember(currentMonthCategorizedExpenses) {
                    currentMonthCategorizedExpenses.sumOf { if (it.isIncome) -it.amount else it.amount }
                }

                val categoryBreakdown = remember(currentMonthCategorizedExpenses) {
                    currentMonthCategorizedExpenses
                        .filter { !it.isIncome }
                        .groupBy { it.category ?: "Other" }
                        .mapValues { (_, list) -> list.sumOf { it.amount } }
                        .toList()
                        .sortedByDescending { it.second }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 2. The Hero Card (Current Month Spend)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("insights_hero_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "THIS MONTH'S SPEND",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Text(
                                    text = formatFormatter.format(currentMonthTotal),
                                    style = MaterialTheme.typography.displayLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-1.5).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // 3. The Category Breakdown List (Visual Analytics)
                    if (categoryBreakdown.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No IndusInd spending detected this month.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "CATEGORY BREAKDOWN",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                DonutChart(
                                    categoryAmounts = categoryBreakdown.toMap(),
                                    totalAmount = currentMonthTotal,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
            FinboxTab.CONTROL_CENTER -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // System Warning Banner / Config
                    item {
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
                                                text = "IndusInd Email Sync Paused",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Grant Notification Access to allow Finbox to securely scan Gmail for IndusInd credit card alerts.",
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
                                        Text("Configure Access", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Gmail Interceptor Active",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Listening for IndusInd credit card e-mail notification bursts securely.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sandbox section
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
                                        "Alert: Your IndusInd Credit Card spent at SWIGGY is Approved for INR 450.00.",
                                        "Transaction at AMAZON PAY is Approved on your IndusInd Credit Card for INR 1,250.50.",
                                        "Hello, transaction at UBER is Approved on your IndusInd CC for INR 320.00."
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

                    // Master data wipe action
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
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
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = "Sweep",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Master Database Sweep",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = "Permanently clear all user transactions from local SQLite cache. This operation is fully irreversible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.clearAllData() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .testTag("reset_database_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Clear All Data", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
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

@Composable
fun DonutChart(
    categoryAmounts: Map<String, Double>,
    totalAmount: Double,
    modifier: Modifier = Modifier
) {
    if (categoryAmounts.isEmpty() || totalAmount <= 0.0) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No spending data this month",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val categoriesList = categoryAmounts.keys.toList()
    var activeSliceIndex by remember { mutableStateOf(-1) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut circle Canvas
        Box(
            modifier = Modifier
                .size(130.dp)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(categoryAmounts) {
                        detectTapGestures { offset ->
                            val center = size.width / 2f
                            val x = offset.x - center
                            val y = offset.y - center
                            val distance = sqrt(x * x + y * y)
                            // check if inside the donut outer/inner region
                            if (distance in (center * 0.4f)..(center * 1.15f)) {
                                var angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toFloat()
                                if (angle < 0) angle += 360f

                                var currentAngle = 0f
                                var matchedIndex = -1
                                for (i in categoriesList.indices) {
                                    val cat = categoriesList[i]
                                    val amt = categoryAmounts[cat] ?: 0.0
                                    val sweep = ((amt / totalAmount) * 360f).toFloat()
                                    val startAngle = currentAngle
                                    val endAngle = currentAngle + sweep
                                    
                                    if (angle >= startAngle && angle < endAngle) {
                                        matchedIndex = i
                                        break
                                    }
                                    currentAngle += sweep
                                }
                                activeSliceIndex = if (activeSliceIndex == matchedIndex) -1 else matchedIndex
                            } else {
                                activeSliceIndex = -1
                            }
                        }
                    }
                    .testTag("donut_chart_canvas")
            ) {
                var startAngle = 0f
                val strokeWidth = 24.dp.toPx()
                val radius = (size.width - strokeWidth) / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                categoriesList.forEachIndexed { index, cat ->
                    val color = getCategoryUiOf(cat).color
                    val amount = categoryAmounts[cat] ?: 0.0
                    val sweepAngle = ((amount / totalAmount) * 360f).toFloat()
                    
                    val isHighlighted = activeSliceIndex == index || activeSliceIndex == -1
                    val alpha = if (isHighlighted) 1.0f else 0.35f
                    val extraStroke = if (activeSliceIndex == index) 4.dp.toPx() else 0f

                    drawArc(
                        color = color.copy(alpha = alpha),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = strokeWidth + extraStroke, cap = StrokeCap.Round)
                    )
                    startAngle += sweepAngle
                }
            }

            // central display inside the donut hole
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val label = if (activeSliceIndex in categoriesList.indices) {
                    categoriesList[activeSliceIndex]
                } else {
                    "Total"
                }

                val value = if (activeSliceIndex in categoriesList.indices) {
                    categoryAmounts[categoriesList[activeSliceIndex]] ?: 0.0
                } else {
                    totalAmount
                }

                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        fontSize = 9.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "₹${String.format("%,.0f", value)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    textAlign = TextAlign.Center
                )
                if (activeSliceIndex in categoriesList.indices) {
                    val pct = ((value / totalAmount) * 100).toInt()
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = getCategoryUiOf(label).color,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            }
        }

        // Legend of categories on the right side
        Column(
            modifier = Modifier.weight(1.2f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categoriesList.forEachIndexed { index, cat ->
                val color = getCategoryUiOf(cat).color
                val amount = categoryAmounts[cat] ?: 0.0
                val pct = ((amount / totalAmount) * 100).toInt()
                val isSelectedOrAll = activeSliceIndex == index || activeSliceIndex == -1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (activeSliceIndex == index) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else Color.Transparent
                        )
                        .clickable {
                            activeSliceIndex = if (activeSliceIndex == index) -1 else index
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = if (isSelectedOrAll) 1f else 0.4f))
                        )
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (activeSliceIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelectedOrAll) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isSelectedOrAll) color else color.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

