package com.chetan.minfinance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE isCategorized = 0 ORDER BY timestamp DESC")
    fun getUncategorizedExpensesFlow(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isCategorized = 1 ORDER BY timestamp DESC")
    fun getCategorizedExpensesFlow(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun getAllExpensesFlow(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun clearAllExpenses()
}
