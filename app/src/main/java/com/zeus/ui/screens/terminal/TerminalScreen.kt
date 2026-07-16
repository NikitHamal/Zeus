
package com.zeus.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(navController: NavController, localPath: String, viewModel: TerminalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(localPath) { viewModel.init(localPath) }

    Scaffold(topBar = { TopAppBar(title = { Text("Terminal - ${state.currentDir}") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color.Black).padding(12.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.logs) { log ->
                    Text(log, color = if (log.startsWith("$")) Color.Green else Color.White, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row {
                Text("$ ", color = Color.Green, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace)
                )
            }
            Button(onClick = { viewModel.execute(input); input = "" }, modifier = Modifier.fillMaxWidth()) { Text("Run") }
        }
    }
}
