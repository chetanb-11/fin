package com.chetan.minfinance.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.chetan.minfinance.data.AppDatabase
import com.chetan.minfinance.data.Expense
import com.chetan.minfinance.data.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import androidx.core.app.NotificationManagerCompat

class SmsNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ExpenseRepository

    // Allowed financial app bundles to save system battery
    private val targetPackages = setOf(
        "com.google.android.gm"     // Gmail
    )

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = ExpenseRepository(database.expenseDao())
        Log.d(TAG, "SmsNotificationListenerService target initialized.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        
        // Guardrail: Target Gmail package exclusively
        if (packageName != "com.google.android.gm") {
            return
        }

        val extras = sbn.notification.extras ?: return
        val title = extras.getString(android.app.Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // Combine Title, Text, and Big Text
        val combinedPayload = "$title $text $bigText".trim()

        // Strict Guardrail: Check for IndusInd (case-insensitive)
        if (!combinedPayload.contains("IndusInd", ignoreCase = true)) {
            return
        }

        // Debug print to Logcat so you can check exact formats via USB connection
        Log.d("FinboxSupport", "Intercepted Target [$packageName]: $combinedPayload")

        val parsed = NotificationParser.parse(combinedPayload)

    if (parsed != null) {
        Log.d("FinboxSupport", "Parsing Success: ${parsed.merchantName} | Rs. ${parsed.amount}")
        serviceScope.launch {
            val expense = com.chetan.minfinance.data.Expense(
                merchantName = parsed.merchantName,
                amount = parsed.amount,
                timestamp = System.currentTimeMillis(),
                category = null,
                isCategorized = false
            )
            repository.insertExpense(expense)
        }
    } else {
        Log.d("FinboxSupport", "Parsing Dropped: Text did not match any compiled regex patterns.")
    }
}

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    companion object {
        private const val TAG = "FinanceInterceptor"

        fun isPermissionGranted(context: Context): Boolean {
            val cn = ComponentName(context, SmsNotificationListenerService::class.java)
            val flat = Settings.Secure.getString(
                context.contentResolver, 
                "enabled_notification_listeners"
            )
            val isEnabledBySettings = flat != null && (
                flat.contains(cn.flattenToString()) || 
                flat.contains(cn.flattenToShortString()) ||
                flat.contains(context.packageName)
            )

            // Direct check using helper API as well
            val isEnabledByCompat = NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

            return isEnabledBySettings || isEnabledByCompat
        }
    }
}
