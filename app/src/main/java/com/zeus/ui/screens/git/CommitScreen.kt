
package com.zeus.ui.screens.git

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitScreen(navController: NavController, localPath: String, viewModel: CommitViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(localPath) { viewModel.load(localPath) }

    Scaffold(topBar = { TopAppBar(title = { Text("Commit") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Changes: ${state.status.modified.size + state.status.untracked.size}", style = MaterialTheme.typography.titleSmall)
                    Text(if (state.status.isClean) "Working tree clean" else "Uncommitted changes", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.status.modified + state.status.untracked) { file ->
                    ListItem(headlineContent = { Text(file) }, leadingContent = { Checkbox(checked = state.staged.contains(file), onCheckedChange = { viewModel.toggleStage(file) }) })
                }
            }
            OutlinedTextField(value = state.message, onValueChange = { viewModel.setMessage(it) }, label = { Text("Commit message") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.commit { navController.popBackStack() } }, enabled = state.message.isNotBlank()) { Text("Commit") }
                OutlinedButton(onClick = { viewModel.push() }) { Text("Push") }
                OutlinedButton(onClick = { viewModel.push(force = true) }) { Text("Force Push") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
