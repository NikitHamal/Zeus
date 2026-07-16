
package com.zeus.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Author Config", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = state.authorName, onValueChange = { viewModel.setName(it) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.authorEmail, onValueChange = { viewModel.setEmail(it) }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { viewModel.saveAuthor() }) { Text("Save") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleMedium)
                    Text("Token: ${state.tokenPreview}")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.logout { navController.navigate("login") { popUpTo(0) } } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Logout") }
                }
            }
            Text("Zeus v1.0.0 - Minimalist GitHub Mobile IDE", style = MaterialTheme.typography.bodySmall)
        }
    }
}
