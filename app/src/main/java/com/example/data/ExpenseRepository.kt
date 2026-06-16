package com.example.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val uncategorizedExpenses: Flow<List<Expense>> = expenseDao.getUncategorizedExpensesFlow()
    val categorizedExpenses: Flow<List<Expense>> = expenseDao.getCategorizedExpensesFlow()
    val allExpenses: Flow<List<Expense>> = expenseDao.getAllExpensesFlow()

    suspend fun insertExpense(expense: Expense): Long {
        return expenseDao.insertExpense(expense)
    }

    suspend fun updateExpense(expense: Expense) {
        expenseDao.updateExpense(expense)
    }

    suspend fun deleteExpense(expense: Expense) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun clearAllFlows() {
        expenseDao.clearAllExpenses()
    }
}
