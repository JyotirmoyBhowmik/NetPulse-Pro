package com.aistudio.netpulse.qpzwtr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.aistudio.netpulse.qpzwtr.data.NetworkRepository
import com.aistudio.netpulse.qpzwtr.ui.DashboardScreen
import com.aistudio.netpulse.qpzwtr.ui.NetworkViewModel
import com.aistudio.netpulse.qpzwtr.ui.theme.MyApplicationTheme

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
                    color = com.aistudio.netpulse.qpzwtr.ui.theme.CyberBlack
                ) {
                    DashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}
