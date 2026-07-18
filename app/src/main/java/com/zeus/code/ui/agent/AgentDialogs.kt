package com.zeus.code.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.model.AgentProject

@Composable
fun AgentProjectPickerDialog(
    state: AgentUiState,
    onDismiss: () -> Unit,
    onSelect: (AgentProject) -> Unit,
    onAddProject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.projects.isEmpty()) {
                    Text("Add a GitHub repository before starting a task.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.height(340.dp)) {
                        items(state.projects, key = { it.id }) { project ->
                            ListItem(
                                headlineContent = {
                                    Text(project.repoFullName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                },
                                supportingContent = {
                                    Text(project.preferredBaseBranch, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = { Icon(Icons.Rounded.Source, null) },
                                modifier = Modifier.clickable { onSelect(project) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                FilledTonalButton(onClick = onAddProject, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add GitHub project")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun AgentAddProjectDialog(
    state: AgentUiState,
    viewModel: BackgroundAgentViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            viewModel.clearRepositorySelection()
            onDismiss()
        },
        title = { Text(if (state.selectedRepository == null) "Add GitHub project" else "Choose base branch") },
        text = {
            if (state.selectedRepository == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = state.repositoryQuery,
                        onValueChange = viewModel::setRepositoryQuery,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search repositories") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true
                    )
                    LazyColumn(Modifier.height(390.dp)) {
                        items(state.filteredRepositories, key = { it.id }) { repository ->
                            ListItem(
                                headlineContent = {
                                    Text(repository.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                },
                                supportingContent = {
                                    Text(
                                        repository.description.ifBlank { repository.defaultBranch },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = { Icon(if (repository.private) Icons.Rounded.Lock else Icons.Rounded.Source, null) },
                                modifier = Modifier.clickable { viewModel.selectRepository(repository) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(state.selectedRepository.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("Select the branch every new task should start from.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LazyColumn(Modifier.height(330.dp)) {
                        items(state.branches, key = { it.name }) { branch ->
                            Row(
                                Modifier.fillMaxWidth().clickable { viewModel.selectBranch(branch.name) }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.AccountTree, null)
                                    Spacer(Modifier.width(10.dp))
                                    Text(branch.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                RadioButton(selected = state.selectedBranch == branch.name, onClick = { viewModel.selectBranch(branch.name) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (state.selectedRepository != null) {
                Button(
                    onClick = { viewModel.addSelectedProject(onDismiss) },
                    enabled = state.selectedBranch.isNotBlank() && !state.busy
                ) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Add project")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (state.selectedRepository != null) viewModel.clearRepositorySelection() else onDismiss()
            }) { Text(if (state.selectedRepository != null) "Back" else "Cancel") }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun AgentConfirmDialog(
    title: String,
    body: String,
    destructive: Boolean,
    confirmLabel: String = if (destructive) "Delete" else "Continue",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ) else ButtonDefaults.buttonColors()
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
