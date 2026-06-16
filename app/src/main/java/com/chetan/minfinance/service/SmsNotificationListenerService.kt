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

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = ExpenseRepository(database.expenseDao())
        Log.d(TAG, "SmsNotificationListenerService started and running.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val extras = sbn.notification.extras ?: return
        
        val title = extras.getString(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        Log.d(TAG, "Notification received from package: $packageName, title: $title")

        // Try parsing primary text first, then bigText as a backup
        var parsed = NotificationParser.parse(text)
        if (parsed == null && bigText.isNotEmpty()) {
            parsed = NotificationParser.parse(bigText)
        }

        if (parsed != null) {
            Log.d(TAG, "Parsed expense: ${parsed.merchantName} of Rs. ${parsed.amount}")
            serviceScope.launch {
                val expense = Expense(
                    merchantName = parsed.merchantName,
                    amount = parsed.amount,
                    timestamp = System.currentTimeMillis(),
                    category = null,
                    isCategorized = false
                )
                repository.insertExpense(expense)
                Log.d(TAG, "Expense successfully inserted into Room database.")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Opt-in cleanup hook if ever needed
    }

    companion object {
        private const val TAG = "ScannerListenerService"

        /**
         * Checks whether notification listener permission is granted for this app.
         */
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
