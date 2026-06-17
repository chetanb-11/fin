package com.chetan.minfinance.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chetan.minfinance.data.AppDatabase
import com.chetan.minfinance.data.Expense
import com.chetan.minfinance.data.ExpenseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PaytmAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: ExpenseRepository

    // Mechanism to prevent duplicate captures for the same screen state
    private var lastProcessedSignature: String? = null
    private var lastProcessedTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        val database = AppDatabase.getInstance(this)
        repository = ExpenseRepository(database.expenseDao())
        Log.d("FinboxScraper", "PaytmAccessibilityService connected. Database and repository initialized.")
    }

    override fun onInterrupt() {
        Log.d("FinboxScraper", "PaytmAccessibilityService interrupted.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        if (packageName != "net.one97.paytm") {
            return
        }

        val rootNode = rootInActiveWindow ?: return
        try {
            val texts = mutableListOf<String>()
            traverseNode(rootNode, texts)

            val flattenedString = texts.joinToString("\n")
            Log.d("FinboxScraper", "Raw extracted screen representation:\n$flattenedString")

            // Verify if screen indicates a successful payment
            if (flattenedString.contains("Payment Successful", ignoreCase = true) ||
                flattenedString.contains("Paid Successfully", ignoreCase = true)) {

                // Regex matching both ₹ and Rs currencies/amounts
                val amountRegex = Regex("""(?:₹|Rs\.?)\s*([0-9,]+(?:\.[0-9]+)?)""")
                val amountMatch = amountRegex.find(flattenedString)
                val amount = if (amountMatch != null) {
                    val amountStr = amountMatch.groupValues[1].replace(",", "")
                    amountStr.toDoubleOrNull() ?: 0.0
                } else {
                    0.0
                }

                // Extract Merchant Name (typically the text block immediately following "Paid to")
                var merchantName = "Unknown Merchant"
                val paidToIndex = texts.indexOfFirst { it.trim().equals("Paid to", ignoreCase = true) }
                if (paidToIndex != -1 && paidToIndex + 1 < texts.size) {
                    merchantName = texts[paidToIndex + 1].trim()
                } else {
                    // Fallback to searching inside single lines
                    for (text in texts) {
                        val trimmed = text.trim()
                        val index = trimmed.indexOf("Paid to", ignoreCase = true)
                        if (index != -1) {
                            val after = trimmed.substring(index + "Paid to".length).trim().removePrefix(":").trim()
                            if (after.isNotEmpty()) {
                                merchantName = after
                                break
                            }
                        }
                    }
                }

                // Deduplication check: ignore if same merchant and amount within 10 seconds
                val currentSignature = "${merchantName}_$amount"
                val currentTime = System.currentTimeMillis()
                if (currentSignature == lastProcessedSignature && currentTime - lastProcessedTime < 10000) {
                    Log.d("FinboxScraper", "Duplicate capture ignored for signature: $currentSignature")
                    return
                }

                lastProcessedSignature = currentSignature
                lastProcessedTime = currentTime

                Log.d("FinboxScraper", "Parsed Data - Merchant: $merchantName, Amount: $amount")

                // Launch coroutine to insert new Expense record
                serviceScope.launch {
                    val expense = Expense(
                        merchantName = merchantName,
                        amount = amount,
                        timestamp = currentTime,
                        category = null,
                        isCategorized = false,
                        isIncome = false
                    )
                    val id = repository.insertExpense(expense)
                    Log.d("FinboxScraper", "Successfully inserted accessibility captured expense record. ID: $id")
                }
            }
        } catch (e: Exception) {
            Log.e("FinboxScraper", "Error processing accessibility event", e)
        }
    }

    private fun traverseNode(node: AccessibilityNodeInfo?, texts: MutableList<String>) {
        if (node == null) return

        val textValue = node.text?.toString()?.trim() ?: ""
        if (textValue.isNotEmpty()) {
            texts.add(textValue)
        }

        val contentDescValue = node.contentDescription?.toString()?.trim() ?: ""
        if (contentDescValue.isNotEmpty() && contentDescValue != textValue) {
            texts.add(contentDescValue)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNode(child, texts)
            }
        }
    }

    companion object {
        fun isPermissionGranted(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, PaytmAccessibilityService::class.java).flattenToString()
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                if (componentNameString.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
