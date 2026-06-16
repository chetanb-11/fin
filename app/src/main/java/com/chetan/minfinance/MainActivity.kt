package com.chetan.minfinance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.chetan.minfinance.data.AppDatabase
import com.chetan.minfinance.data.ExpenseRepository
import com.chetan.minfinance.ui.DashboardScreen
import com.chetan.minfinance.ui.ExpenseViewModel
import com.chetan.minfinance.ui.ExpenseViewModelFactory
import com.chetan.minfinance.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Instantiate simple database and repository
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val repository by lazy { ExpenseRepository(database.expenseDao()) }

    // Use viewModels delegate helper to instantiate with Factory
    private val viewModel: ExpenseViewModel by viewModels {
        ExpenseViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
