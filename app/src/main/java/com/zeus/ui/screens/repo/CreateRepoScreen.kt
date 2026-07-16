
package com.zeus.ui.screens.repo

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
fun CreateRepoScreen(navController: NavController, viewModel: CreateRepoViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("New Repository") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = state.name, onValueChange = { viewModel.setName(it) }, label = { Text("Repo name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.description, onValueChange = { viewModel.setDesc(it) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = state.isPrivate, onCheckedChange = { viewModel.setPrivate(it) })
                Text("Private")
            }
            Button(onClick = { viewModel.create { navController.popBackStack() } }, enabled = state.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create Repository") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
