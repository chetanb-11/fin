package com.chetan.minfinance.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.chetan.minfinance.data.Expense
import com.chetan.minfinance.service.SmsNotificationListenerService
import com.chetan.minfinance.ui.theme.*
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

// Struct to represent beautifully mocked premium cards
data class CreditCardModel(
    val cardName: String,
    val cardType: String,
    val limit: Double,
    val lastFour: String,
    val backgroundBrush: Brush,
    val isLive: Boolean // True if connected to room DB transactions
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Local theme toggle state
    var isDarkTheme by remember { mutableStateOf(true) }

    // Wrap entire layout tree inside our custom redesigned theme!
    MyApplicationTheme(darkTheme = isDarkTheme) {
        val uncategorizedExpenses by viewModel.uncategorizedExpenses.collectAsStateWithLifecycle()
        val categorizedExpenses by viewModel.categorizedExpenses.collectAsStateWithLifecycle()

        // Notification permission detection state
        var isPermissionGranted by remember {
            mutableStateOf(SmsNotificationListenerService.isPermissionGranted(context))
        }

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

        // Form/Sheet States
        var showBottomSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var searchHistoryQuery by remember { mutableStateOf("") }
        var mockSmsText by remember { mutableStateOf("") }

        // Form state inputs for BottomSheet
        var formAmount by remember { mutableStateOf("") }
        var formMerchantName by remember { mutableStateOf("") }
        var formSelectedCategory by remember { mutableStateOf(CATEGORIES.first().name) }
        var formIsIncome by remember { mutableStateOf(false) }

        // Dismissible insight states
        var showInsightDining by remember { mutableStateOf(true) }
        var showInsightScore by remember { mutableStateOf(true) }

        val formatFormatter = remember { DecimalFormat("₹#,##0.00") }
        val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

        // Calculate dynamic metrics based on database
        val liveCardSpent = remember(categorizedExpenses, uncategorizedExpenses) {
            val categorisedSum = categorizedExpenses.filter { !it.isIncome }.sumOf { it.amount }
            val uncategorisedSum = uncategorizedExpenses.sumOf { it.amount }
            categorisedSum + uncategorisedSum
        }

        val liveCardIncome = remember(categorizedExpenses) {
            categorizedExpenses.filter { it.isIncome }.sumOf { it.amount }
        }

        // Outstanding is essentially the accumulated billing spend minus any credit/income payments
        val activeOutstandingBalance = remember(liveCardSpent, liveCardIncome) {
            val bal = liveCardSpent - liveCardIncome
            if (bal < 0.0) 0.0 else bal
        }

        val liveCardLimit = 250000.0 // 2.5 Lakh limit
        val liveCardAvailableCredit = remember(activeOutstandingBalance) {
            val av = liveCardLimit - activeOutstandingBalance
            if (av < 0.0) 0.0 else av
        }

        val liveCardUtilization = remember(activeOutstandingBalance) {
            ((activeOutstandingBalance / liveCardLimit) * 100).coerceAtMost(100.0)
        }

        // Define card gradient brushes
        val cardsList = remember(activeOutstandingBalance, liveCardUtilization) {
            listOf(
                CreditCardModel(
                    cardName = "IndusInd Legend",
                    cardType = "VISA SIGNATURE",
                    limit = liveCardLimit,
                    lastFour = "8421",
                    backgroundBrush = Brush.linearGradient(
                        colors = listOf(CardGradVioletStart, CardGradVioletEnd)
                    ),
                    isLive = true
                ),
                CreditCardModel(
                    cardName = "IndusInd eazyDiner",
                    cardType = "PLATINUM PAY",
                    limit = 150000.0,
                    lastFour = "2640",
                    backgroundBrush = Brush.linearGradient(
                        colors = listOf(CardGradGoldStart, CardGradGoldEnd)
                    ),
                    isLive = false
                ),
                CreditCardModel(
                    cardName = "IndusInd Nexxt",
                    cardType = "MASTERCARD ELITE",
                    limit = 300000.0,
                    lastFour = "9901",
                    backgroundBrush = Brush.linearGradient(
                        colors = listOf(CardGradTealStart, CardGradTealEnd)
                    ),
                    isLive = false
                )
            )
        }

        // Filter transaction lists for the Feed
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

        // Entire layout built as a single continuous-scrolling LazyColumn
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "INDUS",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "FINANCE",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Light,
                                    letterSpacing = 2.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    },
                    actions = {
                        // Global Theme Toggle (Sun/Moon icon)
                        IconButton(
                            onClick = { isDarkTheme = !isDarkTheme },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                    )
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showBottomSheet = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Transaction") },
                    text = { Text("Record", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("fab_add_expense")
                )
            },
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                // SECTION 1: The "One-Glance" Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "TOTAL OUTSTANDING BALANCE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Geometric layout using Tabular Numbers
                        Text(
                            text = formatFormatter.format(activeOutstandingBalance),
                            style = TextStyle(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AVAILABLE CREDIT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatFormatter.format(liveCardAvailableCredit),
                                    style = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = NeonGreen
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "NEXT DUE DATE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "July 12, 2026",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SoftRed
                                )
                            }
                        }
                    }
                }

                // SECTION 2: Swipeable Credit Cards Carousel
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "YOUR ACTIVE DIGITAL CARDS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(cardsList) { card ->
                                val cardBalance = if (card.isLive) activeOutstandingBalance else (card.limit * 0.12)
                                val cardUtilization = if (card.isLive) liveCardUtilization else 12.0

                                Card(
                                    modifier = Modifier
                                        .width(300.dp)
                                        .height(180.dp),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(card.backgroundBrush)
                                            .padding(20.dp)
                                    ) {
                                        // Card Decor Pattern
                                        Box(
                                            modifier = Modifier
                                                .size(200.dp)
                                                .background(Color.White.copy(alpha = 0.03f), shape = CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )

                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column {
                                                    Text(
                                                        text = card.cardName.uppercase(),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White,
                                                        letterSpacing = 1.sp
                                                    )
                                                    Text(
                                                        text = card.cardType,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White.copy(alpha = 0.6f)
                                                    )
                                                }
                                                // Intelligent card chip layout
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp, 24.dp)
                                                        .background(Color(0xFFE5A93C), shape = RoundedCornerShape(4.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "••••  ••••  ••••  ${card.lastFour}",
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color.White.copy(alpha = 0.9f)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "CARD DEBT",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White.copy(alpha = 0.5f)
                                                    )
                                                    Text(
                                                        text = formatFormatter.format(cardBalance),
                                                        style = TextStyle(
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFeatureSettings = "tnum"
                                                        ),
                                                        color = Color.White
                                                    )
                                                }

                                                // Progress Ring inside Card
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Column(horizontalAlignment = Alignment.End) {
                                                        Text(
                                                            text = "UTILIZATION",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White.copy(alpha = 0.5f)
                                                        )
                                                        Text(
                                                            text = "${String.format("%.1f", cardUtilization)}%",
                                                            style = TextStyle(
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFeatureSettings = "tnum"
                                                            ),
                                                            color = if (cardUtilization > 60) SoftRed else NeonGreen
                                                        )
                                                    }

                                                    Canvas(modifier = Modifier.size(28.dp)) {
                                                        // Track arc
                                                        drawArc(
                                                            color = Color.White.copy(alpha = 0.2f),
                                                            startAngle = -90f,
                                                            sweepAngle = 360f,
                                                            useCenter = false,
                                                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                                        )
                                                        // Active utilization Arc
                                                        drawArc(
                                                            color = if (cardUtilization > 60) SoftRed else NeonGreen,
                                                            startAngle = -90f,
                                                            sweepAngle = ((cardUtilization / 100.0) * 360f).toFloat(),
                                                            useCenter = false,
                                                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3: Actionable Insight Cards
                if (showInsightDining || showInsightScore) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SMART FINANCIAL INSIGHTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            AnimatedVisibility(
                                visible = showInsightDining,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = Color(0xFFFF7043).copy(alpha = 0.15f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fastfood,
                                                contentDescription = "Food Alert",
                                                tint = Color(0xFFFF7043)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Dining spent is up 20%",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "You've spent more than your typical budget on restaurant and delivery orders this month. Consider cooking to save.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = { showInsightDining = false },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showInsightScore,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = NeonGreen.copy(alpha = 0.15f),
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TrendingUp,
                                                contentDescription = "Credit Score",
                                                tint = NeonGreen
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Earn up to 200 Reward Coins",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Completing your credit card repayments 5 days early dynamically elevates your CIBIL profile health metric.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }
                                        IconButton(
                                            onClick = { showInsightScore = false },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 4: Uncategorized Alerts Inbox (Frictionless action)
                if (uncategorizedExpenses.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MarkChatUnread,
                                        contentDescription = "Inbox Pending",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "FINANCIAL INBOX",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.background,
                                ) {
                                    Text(
                                        text = "${uncategorizedExpenses.size} Uncategorized",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    items(
                        items = uncategorizedExpenses,
                        key = { it.id }
                    ) { item ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .animateItem()
                        ) {
                            UncategorizedExpenseCard(
                                expense = item,
                                formatter = formatFormatter,
                                onCategorize = { cat -> viewModel.categorizeExpense(item, cat) },
                                onDelete = { viewModel.deleteExpense(item) },
                                onClick = {
                                    // Preset sheet inputs for instant modification
                                    formAmount = item.amount.toString()
                                    formMerchantName = item.merchantName
                                    formIsIncome = item.isIncome
                                    showBottomSheet = true
                                }
                            )
                        }
                    }
                }

                // SECTION 5: Smart Transactions Feed Header
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SMART TRANSACTIONS FEED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Scannable glass-like filter search bar
                        OutlinedTextField(
                            value = searchHistoryQuery,
                            onValueChange = { searchHistoryQuery = it },
                            placeholder = { Text("Search by merchant or category...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                if (searchHistoryQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchHistoryQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_search_input"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                            )
                        )
                    }
                }

                // Transaction Feed Listing
                if (filteredHistory.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "Empty History",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (searchHistoryQuery.trim().isEmpty()) "No history recorded yet." else "No transactions match \"$searchHistoryQuery\"",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Create manual records via the Add FAB or feed mock texts into the sandbox sandbox below.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                } else {
                    items(
                        items = filteredHistory,
                        key = { it.id }
                    ) { item ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .animateItem()
                        ) {
                            CategorizedExpenseCard(
                                expense = item,
                                formatter = formatFormatter,
                                onUncategorize = {
                                    viewModel.categorizeExpense(
                                        item.copy(isCategorized = false, category = null), ""
                                    )
                                },
                                onDelete = { viewModel.deleteExpense(item) },
                                onClick = {
                                    // Trigger editing or modifying
                                    formAmount = item.amount.toString()
                                    formMerchantName = item.merchantName
                                    formIsIncome = item.isIncome
                                    formSelectedCategory = item.category ?: CATEGORIES.first().name
                                    showBottomSheet = true
                                }
                            )
                        }
                    }
                }

                // SECTION 6: Collaborative Payment SMS Sandbox & Controls (Collapsed beautifully)
                item {
                    var isSandboxExpanded by remember { mutableStateOf(false) }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 28.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .clickable { isSandboxExpanded = !isSandboxExpanded }
                                .padding(16.dp)
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
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Control Center",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "UTILITY CONTROL CENTER & SANDBOX",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = if (isSandboxExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand controls",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(
                                visible = isSandboxExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .clickable(enabled = false) {}, // Avoid closing on click inside
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                                    // Interceptor Permission Alerts
                                    if (!isPermissionGranted) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Gmail Interceptor Inactive",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SoftRed
                                            )
                                            Text(
                                                text = "To let Indus Finance index automatic notifications directly from Gmail matching IndusInd alerts.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = {
                                                    context.startActivity(
                                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                        }
                                                    )
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = SoftRed),
                                                modifier = Modifier
                                                    .align(Alignment.End)
                                                    .testTag("grant_permission_button")
                                            ) {
                                                Text("Grant Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(NeonGreen, CircleShape)
                                            )
                                            Text(
                                                text = "Gmail Listener Active & Live",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonGreen
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Sandbox engine controls
                                    Text(
                                        text = "Real-time Notification SMS Sandbox",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Compile and query simulated texts through SMS regex engines:",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    // Presets chips scroll
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
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { mockSmsText = preset }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                                    .testTag("sandbox_chip_$preset")
                                            ) {
                                                Text(
                                                    text = preset,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 140.dp)
                                                )
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = mockSmsText,
                                        onValueChange = { mockSmsText = it },
                                        placeholder = { Text("Synthesize mock notifications here...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("sandbox_sms_textfield"),
                                        textStyle = TextStyle(fontSize = 11.sp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Reset DB trigger
                                        IconButton(
                                            onClick = { viewModel.clearAllData() },
                                            modifier = Modifier.testTag("reset_database_top_shortcut")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteSweep,
                                                contentDescription = "Format Room Database",
                                                tint = SoftRed
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (mockSmsText.trim().isNotEmpty()) {
                                                    val matched = viewModel.simulateNotificationReceipt(mockSmsText)
                                                    if (matched) mockSmsText = ""
                                                }
                                            },
                                            enabled = mockSmsText.trim().isNotEmpty(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.testTag("simulate_button")
                                        ) {
                                            Text("Parse Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 7: Sleek frictionless ModalBottomSheet
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    // Reset form defaults
                    formAmount = ""
                    formMerchantName = ""
                    formIsIncome = false
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "NEW FINANCIAL RECORD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Big Numeric Amount Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "₹",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        OutlinedTextField(
                            value = formAmount,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.toDoubleOrNull() != null || input.all { it.isDigit() }) {
                                    formAmount = input
                                }
                            },
                            placeholder = { Text("0.00", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Left,
                                letterSpacing = (-1).sp,
                                fontFeatureSettings = "tnum"
                            ),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .width(200.dp)
                                .testTag("form_amount_input")
                        )
                    }

                    // Category Chips Horizontal Scroll Row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SELECT CATEGORY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CATEGORIES.forEach { category ->
                                val isSelected = formSelectedCategory == category.name
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { formSelectedCategory = category.name },
                                    label = { Text(category.name, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = category.name,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = category.color.copy(alpha = 0.25f),
                                        selectedLabelColor = category.color,
                                        selectedLeadingIconColor = category.color
                                    ),
                                    modifier = Modifier.testTag("category_chip_${category.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Merchant Field Details
                    OutlinedTextField(
                        value = formMerchantName,
                        onValueChange = { formMerchantName = it },
                        label = { Text("Merchant or Payee Name") },
                        placeholder = { Text("e.g. Swiggy, Amazon, Starbucks") },
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_merchant_input")
                    )

                    // Transaction Type toggle chips (Income / Expense)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable { formIsIncome = false },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (!formIsIncome) SoftRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (!formIsIncome) BorderStroke(1.dp, SoftRed) else null
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Debit", tint = if (!formIsIncome) SoftRed else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("EXPENSE", fontWeight = FontWeight.Bold, color = if (!formIsIncome) SoftRed else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable { formIsIncome = true },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (formIsIncome) NeonGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = if (formIsIncome) BorderStroke(1.dp, NeonGreen) else null
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Credit", tint = if (formIsIncome) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("INCOME", fontWeight = FontWeight.Bold, color = if (formIsIncome) NeonGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Done/Submit Transaction button
                    Button(
                        onClick = {
                            val amt = formAmount.toDoubleOrNull()
                            if (amt != null && formMerchantName.trim().isNotEmpty()) {
                                viewModel.addManualExpense(
                                    merchantName = formMerchantName,
                                    amount = amt,
                                    category = formSelectedCategory,
                                    isIncome = formIsIncome
                                )
                                showBottomSheet = false
                                // reset fields
                                formAmount = ""
                                formMerchantName = ""
                                formIsIncome = false
                            }
                        },
                        enabled = formAmount.isNotEmpty() && formMerchantName.trim().isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("submit_manual_expense")
                    ) {
                        Text("Record Transaction", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}

/**
 * Polished Uncategorized Expense Card for Intercepted Alerts
 * Signatures match GreetingScreenshotTest exactly!
 */
@Composable
fun UncategorizedExpenseCard(
    expense: Expense,
    formatter: DecimalFormat,
    onCategorize: (String) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, SoftRed.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SoftRed.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Intercept Alert",
                            tint = SoftRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = expense.merchantName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                        Text(
                            text = "Pending Review",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SoftRed
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatter.format(expense.amount),
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum"
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Alert",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            // Quick Instant Categorize Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "TAP CHIP TO INSTANT CATEGORIZE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CATEGORIES.forEach { category ->
                        Box(
                            modifier = Modifier
                                .border(
                                    border = BorderStroke(1.dp, category.color.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(category.color.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .clickable { onCategorize(category.name) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = category.name,
                                    tint = category.color,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = category.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = category.color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Polished Categorized Expense Card
 */
@Composable
fun CategorizedExpenseCard(
    expense: Expense,
    formatter: DecimalFormat,
    onUncategorize: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val categoryDetails = getCategoryUiOf(expense.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Merchant Placeholder circular Logo
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(categoryDetails.color.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, categoryDetails.color.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryDetails.icon,
                        contentDescription = expense.category ?: "Other",
                        tint = categoryDetails.color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = expense.merchantName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = categoryDetails.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryDetails.color
                        )
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                        )
                        Text(
                            text = "Card ••21",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (expense.isIncome) "+ ${formatter.format(expense.amount)}" else "- ${formatter.format(expense.amount)}",
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (expense.isIncome) NeonGreen else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(expense.timestamp)),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Options Button
                var showOptionsPopup by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { showOptionsPopup = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsPopup,
                        onDismissRequest = { showOptionsPopup = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Re-review (Uncategorize)") },
                            onClick = {
                                showOptionsPopup = false
                                onUncategorize()
                            },
                            leadingIcon = { Icon(Icons.Default.Undo, contentDescription = "Undo") }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Log", color = SoftRed) },
                            onClick = {
                                showOptionsPopup = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SoftRed) }
                        )
                    }
                }
            }
        }
    }
}
