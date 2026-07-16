
package com.zeus.ui.screens.pr

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun PRListScreen(navController: NavController, fullName: String, viewModel: PRViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(fullName) { viewModel.load(fullName) }
    Scaffold(topBar = { TopAppBar(title = { Text("PRs - $fullName") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.prs) { pr ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("#${pr.number} ${pr.title}", style = MaterialTheme.typography.titleSmall)
                        Text("by ${pr.user.login} • ${pr.state}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.merge(fullName, pr.number) }) { Text("Merge") }
                        }
                    }
                }
            }
        }
    }
}
