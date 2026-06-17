package com.chetan.minfinance.service

import android.util.Log

data class ParsedTransaction(
    val merchantName: String,
    val amount: Double
)

object NotificationParser {
    private const val TAG = "NotificationParser"

    // Patterns designed to extract transactions specifically targeting standard Indian credit card email alerting formats:
    // - "INR 450.00 spent at SWIGGY on"
    // - "amount of INR 1,250.50 has been debited at AMAZON PAY on"
    // - "spent at SWIGGY on your IndusInd Credit Card for INR 450.00"
    private val PATTERNS = listOf(
        // Pattern 0: Amount first, then Merchant
        Regex("(?i)(?:amount of\\s+)?(?:INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?).*?(?:spent\\s+at|debited\\s+at|at)\\s+([^.\\n]+?)\\s+(?:on|at)"),
        
        // Pattern 1: Merchant first, then Amount
        Regex("(?i)(?:spent\\s+at|debited\\s+at|at)\\s+([^.\\n]+?)\\s+(?:on|at|for).*?(?:amount of\\s+)?(?:INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    )

    fun parse(text: String): ParsedTransaction? {
        // Gracefully handle line breaks and extra spaces Gmail sometimes injects
        val sanitizedText = text.replace(Regex("\\s+"), " ").trim()
        Log.d(TAG, "Attempting to parse notification text: \"$sanitizedText\"")
        
        for ((index, regex) in PATTERNS.withIndex()) {
            val match = regex.find(sanitizedText) ?: continue
            try {
                // Determine group order based on match index/structure
                val amountStr: String
                val merchantRaw: String

                if (index == 1) {
                    // Pattern 1 has merchant first, then amount
                    merchantRaw = match.groupValues[1]
                    amountStr = match.groupValues[2]
                } else {
                    // Pattern 0 has amount first, then merchant
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
