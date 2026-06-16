package com.example.service

import android.util.Log

data class ParsedTransaction(
    val merchantName: String,
    val amount: Double
)

object NotificationParser {
    private const val TAG = "NotificationParser"

    // Patterns designed to extract transactions of the form:
    // - "Paid Rs. 150 to Swiggy"
    // - "Sent ₹ 25.50 to Raju Chai"
    // - "Successfully paid Paytm Rs 350 to BigBasket"
    // - "Rs. 450 debited at Petrol Pump"
    // - "Rs. 250 charged on Netflix"
    private val PATTERNS = listOf(
        // Pattern 1: Paid/Sent/Debited X to/at/on Y
        Regex("(?i)(?:Paid|Sent|Transfer|Debited|Charged)\\s*(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:to|at|on|for)\\s+([^.,\\n]+)"),
        
        // Pattern 2: X paid to Y
        Regex("(?i)(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+(?:paid|debited|sent|transferred)\\s+(?:successfully\\s+)?(?:to|at)\\s+([^.,\\n]+)"),
        
        // Pattern 3: Transaction at Y for X
        Regex("(?i)(?:Txn|Transaction)*\\s*at\\s+([^.,\\n]+)\\s+for\\s+(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    )

    fun parse(text: String): ParsedTransaction? {
        Log.d(TAG, "Attempting to parse notification text: \"$text\"")
        
        for ((index, regex) in PATTERNS.withIndex()) {
            val match = regex.find(text) ?: continue
            try {
                // Determine group order based on match index/structure
                val amountStr: String
                val merchantRaw: String

                if (index == 2) {
                    // Pattern 3 has merchant first, then amount
                    merchantRaw = match.groupValues[1]
                    amountStr = match.groupValues[2]
                } else {
                    // Patterns 1 & 2 have amount first, then merchant
                    amountStr = match.groupValues[1]
                    merchantRaw = match.groupValues[2]
                }

                val amount = amountStr.replace(",", "").toDouble()
                var merchant = merchantRaw.trim()

                // Clean up merchant name by removing trailing noise (e.g. UPI ID or platform suffix)
                if (merchant.contains("Ref", ignoreCase = true)) {
                    merchant = merchant.split(Regex("(?i)Ref"))[0].trim()
                }
                if (merchant.contains("using", ignoreCase = true)) {
                    merchant = merchant.split(Regex("(?i)using"))[0].trim()
                }
                if (merchant.contains("via", ignoreCase = true)) {
                    merchant = merchant.split(Regex("(?i)via"))[0].trim()
                }
                if (merchant.contains("ending", ignoreCase = true)) {
                    merchant = merchant.split(Regex("(?i)ending"))[0].trim()
                }

                // If merchant has date strings like "on 12-06", trim it
                val dateSplit = merchant.split(Regex("(?i)\\s+on\\s+"))
                if (dateSplit.size > 1 && dateSplit[1].any { it.isDigit() }) {
                    merchant = dateSplit[0].trim()
                }

                if (merchant.isNotEmpty() && amount > 0) {
                    Log.d(TAG, "Successfully parsed transaction: $merchant, Amount: $amount")
                    return ParsedTransaction(
                        merchantName = merchant,
                        amount = amount
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error matching pattern $index: ${e.message}")
            }
        }
        Log.d(TAG, "No translation matched standard patterns.")
        return null
    }
}
