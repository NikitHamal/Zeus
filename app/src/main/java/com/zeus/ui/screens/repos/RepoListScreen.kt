
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
fun RepoListScreen(navController: NavController, viewModel: RepoListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text("Repositories") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab==0, onClick = { tab=0 }, text = { Text("Local") })
                Tab(selected = tab==1, onClick = { tab=1 }, text = { Text("Remote") })
            }
            when(tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.local) { repo ->
                        ListItem(
                            headlineContent = { Text(repo.name) },
                            supportingContent = { Text(repo.currentBranch) },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.remote) { repo ->
                        Card(onClick = { navController.navigate(Screen.RepoDetail.create(repo.full_name)) }, modifier = Modifier.fillMaxWidth()) {
                            ListItem(headlineContent = { Text(repo.full_name) }, supportingContent = { Text(repo.description ?: "") })
                        }
                    }
                }
            }
        }
    }
}
