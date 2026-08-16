package com.zeus.code.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.code.memory.KnowledgeItem
import com.zeus.code.memory.MemoryManager
import com.zeus.code.memory.MemoryType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    memoryManager: MemoryManager,
    modifier: Modifier = Modifier
) {
    val items by memoryManager.items.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<KnowledgeItem>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedItemForDetail by remember { mutableStateOf<KnowledgeItem?>(null) }
    val scope = rememberCoroutineScope()

    val displayItems = searchResults ?: items

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Knowledge Base & Memory", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "${items.size} memories indexed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Memory")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { q ->
                    searchQuery = q
                    scope.launch {
                        searchResults = if (q.isNotBlank()) memoryManager.searchMemories(q) else null
                    }
                },
                placeholder = { Text("Search memories, rules, architecture notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            searchResults = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Knowledge Memories Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Add architecture notes, coding rules, or documentation chunks. They will automatically be recalled and provided to Zeus agents.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Knowledge Note")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayItems, key = { it.id }) { item ->
                        KnowledgeItemCard(
                            item = item,
                            onClick = { selectedItemForDetail = item },
                            onDelete = { memoryManager.deleteMemory(item.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddKnowledgeDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, content, type, repo, tags ->
                memoryManager.saveMemory(
                    title = title,
                    content = content,
                    type = type,
                    tags = tags,
                    repository = repo
                )
                showAddDialog = false
            }
        )
    }

    if (selectedItemForDetail != null) {
        AlertDialog(
            onDismissRequest = { selectedItemForDetail = null },
            title = { Text(selectedItemForDetail!!.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                selectedItemForDetail!!.type.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        if (selectedItemForDetail!!.repository.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                selectedItemForDetail!!.repository,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        selectedItemForDetail!!.content,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedItemForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun KnowledgeItemCard(
    item: KnowledgeItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = item.type.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.tags.isNotEmpty() || item.repository.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.repository.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "repo:${item.repository}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    item.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddKnowledgeDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, type: MemoryType, repository: String, tags: List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(MemoryType.CODING_RULE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Knowledge Memory", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Kotlin Coroutine Best Practices") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Memory Content") },
                    placeholder = { Text("Rules, architectural decisions, syntax examples...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Repository Scope (Optional)") },
                    placeholder = { Text("owner/repo or blank for global") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("Tags (Comma-separated)") },
                    placeholder = { Text("architecture, ui, compose") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tags = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(title, content, selectedType, repo, tags)
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
