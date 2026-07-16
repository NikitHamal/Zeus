
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
fun BranchManagerScreen(navController: NavController, localPath: String, viewModel: BranchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var newBranch by remember { mutableStateOf("") }
    LaunchedEffect(localPath) { viewModel.load(localPath) }

    Scaffold(topBar = { TopAppBar(title = { Text("Branches") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Current: ${state.current}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(value = newBranch, onValueChange = { newBranch = it }, label = { Text("New branch") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.createBranch(newBranch) }) { Text("Create") }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(state.branches) { branch ->
                    ListItem(
                        headlineContent = { Text(branch) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { viewModel.checkout(branch) }) { Text("Checkout") }
                            }
                        }
                    )
                }
            }
        }
    }
}
