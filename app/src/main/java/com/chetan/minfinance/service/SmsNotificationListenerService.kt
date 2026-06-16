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

class SmsNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ExpenseRepository

    // Allowed financial app bundles to save system battery
    private val targetPackages = setOf(
        "net.one97.paytm",          // Paytm App
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app"           // PhonePe
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
        
        // Performance Guardrail: Drop execution immediately if it's not a financial target
        if (!targetPackages.contains(packageName) && !packageName.contains("sms", ignoreCase = true)) {
            return
        }

        val extras = sbn.notification.extras ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // Try parsing primary text body first, fallback to bigText
        var parsed = NotificationParser.parse(text)
        if (parsed == null && bigText.isNotEmpty()) {
            parsed = NotificationParser.parse(bigText)
        }

        if (parsed != null) {
            Log.d(TAG, "Successfully intercepted transaction: ${parsed.merchantName} -> Rs. ${parsed.amount}")
            serviceScope.launch {
                val expense = Expense(
                    merchantName = parsed.merchantName,
                    amount = parsed.amount,
                    timestamp = System.currentTimeMillis(),
                    category = null,
                    isCategorized = false
                )
                repository.insertExpense(expense)
            }
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
            return flat != null && flat.contains(cn.flattenToString())
        }
    }
}
