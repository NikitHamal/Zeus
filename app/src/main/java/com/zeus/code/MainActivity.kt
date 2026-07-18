package com.zeus.code

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeus.code.ui.MainViewModel
import com.zeus.code.ui.ZeusApp
import com.zeus.code.ui.agent.BackgroundAgentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val agentViewModel: BackgroundAgentViewModel = viewModel()
            ZeusApp(viewModel, agentViewModel)
        }
    }
}
