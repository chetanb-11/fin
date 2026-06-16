package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.ExpenseRepository
import com.example.ui.DashboardScreen
import com.example.ui.ExpenseViewModel
import com.example.ui.ExpenseViewModelFactory
import com.example.ui.theme.MyApplicationTheme

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
