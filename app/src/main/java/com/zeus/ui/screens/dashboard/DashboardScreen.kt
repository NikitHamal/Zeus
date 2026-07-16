
package com.zeus.ui.screens.dashboard

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
import com.zeus.ui.components.ZeusCard
import com.zeus.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Zeus") }, actions = {
                IconButton(onClick = { navController.navigate(Screen.Settings.route) }) { Icon(Icons.Default.Settings, null) }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { navController.navigate(Screen.CreateRepo.route) }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New Repo") })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                ZeusCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Hello, ${state.user?.login ?: "dev"} 👋", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("${state.localRepos.size} local repos • ${state.remoteRepos.size} remote", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    AssistChip(onClick = { navController.navigate(Screen.RepoList.route) }, label = { Text("Local") }, leadingIcon = { Icon(Icons.Default.Folder, null) })
                    AssistChip(onClick = { navController.navigate(Screen.RepoList.route) }, label = { Text("Clone") }, leadingIcon = { Icon(Icons.Default.Download, null) })
                    AssistChip(onClick = { /* import */ }, label = { Text("Import") }, leadingIcon = { Icon(Icons.Default.FolderOpen, null) })
                }
            }
            item { Text("Local Repositories", style = MaterialTheme.typography.titleMedium) }
            items(state.localRepos) { repo ->
                Card(onClick = { navController.navigate(Screen.FileBrowser.create(repo.path)) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(repo.name) },
                        supportingContent = { Text(repo.currentBranch + " • " + (repo.remoteUrl ?: "no remote")) },
                        leadingContent = { Icon(Icons.Default.Folder, null) },
                        trailingContent = { Icon(Icons.Default.ChevronRight, null) }
                    )
                }
            }
            item { Text("Recent Remote", style = MaterialTheme.typography.titleMedium) }
            items(state.remoteRepos.take(10)) { repo ->
                Card(onClick = { navController.navigate(Screen.RepoDetail.create(repo.full_name)) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(repo.full_name) },
                        supportingContent = { Text(repo.description ?: "No description") },
                        leadingContent = { Icon(Icons.Default.Code, null) }
                    )
                }
            }
        }
    }
}
