package com.chetan.minfinance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chetan.minfinance.data.Expense
import com.chetan.minfinance.data.ExpenseRepository
import com.chetan.minfinance.service.NotificationParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    // Retrieve lists Reactively from Room DB Flow
    val uncategorizedExpenses: StateFlow<List<Expense>> = repository.uncategorizedExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val categorizedExpenses: StateFlow<List<Expense>> = repository.categorizedExpenses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun categorizeExpense(expense: Expense, category: String) {
        viewModelScope.launch {
            val updatedExpense = expense.copy(
                category = category,
                isCategorized = true
            )
            repository.updateExpense(updatedExpense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    fun addManualExpense(merchantName: String, amount: Double, category: String? = null) {
        viewModelScope.launch {
            val isCat = category != null
            val expense = Expense(
                merchantName = merchantName.trim(),
                amount = amount,
                timestamp = System.currentTimeMillis(),
                category = category,
                isCategorized = isCat
            )
            repository.insertExpense(expense)
        }
    }

    /**
     * Simulates notification interceptor logic. Takes a text payload, compiles it through 
     * regex, and inserts parsed expense into Room if matched, otherwise returns false.
     */
    fun simulateNotificationReceipt(text: String): Boolean {
        val parsed = NotificationParser.parse(text)
        return if (parsed != null) {
            viewModelScope.launch {
                val expense = Expense(
                    merchantName = parsed.merchantName,
                    amount = parsed.amount,
                    timestamp = System.currentTimeMillis(),
                    category = null,
                    isCategorized = false
                )
                repository.insertExpense(expense)
            }
            true
        } else {
            false
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllFlows()
        }
    }
}

class ExpenseViewModelFactory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExpenseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
