package com.chetan.minfinance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val merchantName: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String? = null,
    val isCategorized: Boolean = false,
    val isIncome: Boolean = false
)
