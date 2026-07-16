package com.zeus.code

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeus.code.ui.MainViewModel
import com.zeus.code.ui.ZeusApp
import com.zeus.code.ui.theme.ZeusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZeusTheme {
                val viewModel: MainViewModel = viewModel()
                ZeusApp(viewModel)
            }
        }
    }
}
