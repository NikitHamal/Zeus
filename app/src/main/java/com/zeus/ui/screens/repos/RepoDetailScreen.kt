
package com.zeus.ui.screens.repos

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
import com.zeus.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(navController: NavController, fullName: String, viewModel: RepoDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(fullName) { viewModel.load(fullName) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(fullName) }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
            actions = {
                IconButton(onClick = { viewModel.fork(fullName) }) { Icon(Icons.Default.CallSplit, null) }
                IconButton(onClick = { /* delete */ }) { Icon(Icons.Default.Delete, null) }
            })
    }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(state.repo?.full_name ?: fullName, style = MaterialTheme.typography.titleLarge)
                        Text(state.repo?.description ?: "", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text("⭐ ${state.repo?.stargazers_count ?: 0}") })
                            AssistChip(onClick = {}, label = { Text("🍴 ${state.repo?.forks_count ?: 0}") })
                            AssistChip(onClick = {}, label = { Text(state.repo?.language ?: "code") })
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { navController.navigate(Screen.PRList.create(fullName)) }) { Text("PRs") }
                    OutlinedButton(onClick = { /* clone */ }) { Text("Clone") }
                    OutlinedButton(onClick = { navController.navigate(Screen.FileBrowser.create(fullName)) }) { Text("Files") }
                }
            }
            item { Text("Branches", style = MaterialTheme.typography.titleMedium) }
            items(state.branches) { b -> ListItem(headlineContent = { Text(b.name) }) }
            item { Text("Recent Commits", style = MaterialTheme.typography.titleMedium) }
            items(state.commits) { c -> ListItem(headlineContent = { Text(c.commit.message) }, supportingContent = { Text(c.sha.take(7) + " • " + c.commit.author.name) }) }
        }
    }
}
