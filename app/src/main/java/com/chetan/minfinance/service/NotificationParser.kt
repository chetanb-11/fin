package com.chetan.minfinance.service

import android.util.Log

data class ParsedTransaction(
    val merchantName: String,
    val amount: Double
)

object NotificationParser {
    fun parse(text: String): ParsedTransaction? {
        // Clean up the text by replacing multiple spaces/newlines with a single space
        val cleanText = text.replace(Regex("\\s+"), " ").trim()
        
        Log.d("FinboxParser", "Analyzing Gmail String: $cleanText")

        // Guardrail: Double-check that this is an IndusInd alert before doing heavy regex math
        if (!cleanText.contains("IndusInd", ignoreCase = true)) {
            Log.d("FinboxParser", "Dropped: Not an IndusInd notification.")
            return null
        }

        try {
            // 1. EXTRACT AMOUNT: 
            // We look specifically for "for INR [Amount]" so we don't accidentally grab the "Available Limit: INR [Amount]"
            val amountRegex = Regex("(?i)for\\s+INR\\s+([\\d,]+(?:\\.\\d{1,2})?)")
            val amountMatch = amountRegex.find(cleanText)
            
            val amountString = amountMatch?.groupValues?.get(1)?.replace(",", "")
            val amount = amountString?.toDoubleOrNull()

            // 2. EXTRACT MERCHANT:
            // We capture everything between " at " and " is Approved"
            val merchantRegex = Regex("(?i)\\bat\\s+(.*?)\\s+is\\s+Approved\\b")
            val merchantMatch = merchantRegex.find(cleanText)
            
            val merchantName = merchantMatch?.groupValues?.get(1)?.trim()

            if (amount != null && merchantName != null) {
                Log.d("FinboxParser", "🎯 Target Acquired! Merchant: $merchantName | Amount: $amount")
                return ParsedTransaction(merchantName, amount)
            } else {
                Log.d("FinboxParser", "Failed to match IndusInd regex patterns. Amount found: $amount, Merchant found: $merchantName")
            }

        } catch (e: Exception) {
            Log.e("FinboxParser", "Regex crash during parsing: ${e.message}")
        }

        return null
    }
}