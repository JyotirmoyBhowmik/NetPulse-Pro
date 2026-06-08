package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.NetworkRepository
import com.example.ui.DashboardScreen
import com.example.ui.NetworkViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Core Architecture Repository Pattern
        val repository = NetworkRepository(applicationContext)

        // Inject using our custom factory
        val viewModel: NetworkViewModel by viewModels {
            NetworkViewModel.Factory(application, repository)
        }

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = com.example.ui.theme.CyberBlack
                ) {
                    DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
