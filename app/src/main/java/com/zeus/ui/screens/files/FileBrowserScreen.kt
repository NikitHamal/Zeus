
package com.zeus.ui.screens.files

import androidx.compose.foundation.clickable
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(navController: NavController, localPath: String, viewModel: FileBrowserViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(localPath) { viewModel.load(localPath) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.currentDir?.name ?: "Files") },
                navigationIcon = { IconButton(onClick = { if (!viewModel.navigateUp()) navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Commit.create(state.currentDir?.absolutePath ?: localPath)) }) { Icon(Icons.Default.Check, null) }
                    IconButton(onClick = { navController.navigate(Screen.History.create(state.currentDir?.absolutePath ?: localPath)) }) { Icon(Icons.Default.History, null) }
                    IconButton(onClick = { navController.navigate(Screen.Branches.create(state.currentDir?.absolutePath ?: localPath)) }) { Icon(Icons.Default.Share, null) }
                    IconButton(onClick = { navController.navigate(Screen.Terminal.create(state.currentDir?.absolutePath ?: localPath)) }) { Icon(Icons.Default.Computer, null) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createFileDialog = true }) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(state.files) { file ->
                ListItem(
                    headlineContent = { Text(file.name) },
                    supportingContent = { Text(if (file.isDirectory) "Folder" else "${file.length()/1024} KB") },
                    leadingContent = { Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null) },
                    modifier = Modifier.clickable {
                        if (file.isDirectory) viewModel.load(file.absolutePath)
                        else navController.navigate(Screen.Editor.create(file.absolutePath, state.rootPath))
                    }
                )
                Divider()
            }
        }
        if (viewModel.createFileDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.createFileDialog = false },
                title = { Text("New file") },
                text = {
                    var name by remember { mutableStateOf("") }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") })
                    // quick action
                    LaunchedEffect(name) { viewModel.pendingFileName = name }
                },
                confirmButton = {
                    Button(onClick = { viewModel.createFile(); viewModel.createFileDialog = false }) { Text("Create") }
                },
                dismissButton = { TextButton(onClick = { viewModel.createFileDialog = false }) { Text("Cancel") } }
            )
        }
    }
}
