package com.zeus.code.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.model.AgentLlmModel
import com.zeus.code.model.AgentLlmProviderEntry
import com.zeus.code.model.AgentLlmSavedProvider
import com.zeus.code.model.AgentLlmTestResponse

// ---------------------------------------------------------------------------
// Model picker — shown before starting a task
// ---------------------------------------------------------------------------

@Composable
fun AgentModelPickerDialog(
    state: AgentUiState,
    onDismiss: () -> Unit,
    onSelectDefault: () -> Unit,
    onSelect: (AgentLlmProviderEntry, String) -> Unit,
    onManageProviders: (AgentLlmProviderEntry?) -> Unit
) {
    val catalog = state.llmCatalog
    val selection = state.llmSelection
    var expanded by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model & provider") },
        text = {
            if (catalog == null) {
                Column(
                    Modifier.fillMaxWidth().height(240.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.height(440.dp)) {
                    item {
                        val communityDefault = catalog.community.firstOrNull { it.selectableForAgent }
                        val defaultLabel = communityDefault?.models
                            ?.firstOrNull { it.id.equals(communityDefault.defaultModel, true) }
                            ?.displayLabel
                            ?: communityDefault?.defaultModel.orEmpty()
                        ListItem(
                            headlineContent = { Text("Server default", fontWeight = FontWeight.SemiBold) },
                            supportingContent = {
                                Text(
                                    if (defaultLabel.isBlank()) "Managed by NEBians" else defaultLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = { RadioButton(selected = selection.isDefault, onClick = onSelectDefault) },
                            modifier = Modifier.clickable(onClick = onSelectDefault)
                        )
                        HorizontalDivider()
                    }
                    val community = catalog.community.filter { it.selectableForAgent }
                    if (community.isNotEmpty()) {
                        item { LlmGroupHeader("Community · free, no key needed") }
                        community.forEach { entry ->
                            item(key = "community-${entry.slug}") {
                                LlmProviderRow(
                                    entry = entry,
                                    selection = selection,
                                    expanded = "community-${entry.slug}" in expanded,
                                    onToggle = {
                                        expanded = toggleKey(expanded, "community-${entry.slug}")
                                    },
                                    onPick = { modelId -> onSelect(entry, modelId) },
                                    onAddKey = null
                                )
                            }
                        }
                    }
                    if (catalog.official.isNotEmpty()) {
                        item { LlmGroupHeader("Official APIs · Agnes, OpenAI, Anthropic, Gemini, DeepSeek") }
                        catalog.official.forEach { entry ->
                            item(key = "official-${entry.slug}") {
                                LlmProviderRow(
                                    entry = entry,
                                    selection = selection,
                                    expanded = "official-${entry.slug}" in expanded,
                                    onToggle = {
                                        expanded = toggleKey(expanded, "official-${entry.slug}")
                                    },
                                    onPick = { modelId -> onSelect(entry, modelId) },
                                    onAddKey = if (entry.available) null else ({ onManageProviders(entry) })
                                )
                            }
                        }
                    }
                    if (catalog.custom.isNotEmpty()) {
                        item { LlmGroupHeader("Your providers") }
                        catalog.custom.forEach { entry ->
                            item(key = "custom-${entry.id}") {
                                LlmProviderRow(
                                    entry = entry,
                                    selection = selection,
                                    expanded = "custom-${entry.id}" in expanded,
                                    onToggle = {
                                        expanded = toggleKey(expanded, "custom-${entry.id}")
                                    },
                                    onPick = { modelId -> onSelect(entry, modelId) },
                                    onAddKey = null
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onManageProviders(null) }) {
                Icon(Icons.Rounded.Settings, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Manage providers")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun toggleKey(set: Set<String>, key: String): Set<String> =
    if (key in set) set - key else set + key

@Composable
private fun LlmGroupHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LlmProviderRow(
    entry: AgentLlmProviderEntry,
    selection: AgentLlmSelection,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPick: (String) -> Unit,
    onAddKey: (() -> Unit)?
) {
    val disabled = !entry.available
    val models = entry.models.ifEmpty {
        entry.defaultModel.takeIf { it.isNotBlank() }?.let { listOf(AgentLlmModel(id = it)) } ?: emptyList()
    }
    Column {
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.label,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (entry.freeNote.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                entry.freeNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            },
            supportingContent = {
                Text(llmRowSupportText(entry), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                Icon(
                    if (expanded && !disabled) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                    null,
                    tint = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            },
            trailingContent = {
                if (onAddKey != null) TextButton(onClick = onAddKey) { Text("Add key") }
            },
            modifier = Modifier.clickable(enabled = !disabled, onClick = onToggle)
        )
        if (expanded && !disabled) {
            models.forEach { model ->
                val selected = !selection.isDefault &&
                    selection.provider == entry.slug &&
                    selection.model.equals(model.id, true) &&
                    (entry.id.isBlank() || selection.providerId == entry.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(model.id) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { onPick(model.id) })
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(model.displayLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (model.note.isNotBlank()) {
                            Text(
                                model.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun llmRowSupportText(entry: AgentLlmProviderEntry): String {
    val parts = mutableListOf<String>()
    if (!entry.available) {
        parts += "Add your API key to enable"
    } else {
        when (entry.keySource) {
            "byok" -> parts += if (entry.keyMasked.isNotBlank()) "Your key ${entry.keyMasked}" else "Your key"
            "bot" -> parts += "Shared workspace key"
            "env" -> parts += "Server key"
            "scraper" -> parts += "Community, no key needed"
            else -> if (entry.keyMasked.isNotBlank()) parts += entry.keyMasked
        }
    }
    if (entry.models.isNotEmpty()) parts += "${entry.models.size} model" + (if (entry.models.size == 1) "" else "s")
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------------------
// Provider manager — BYOK keys + custom providers
// ---------------------------------------------------------------------------

@Composable
fun AgentProvidersDialog(
    state: AgentUiState,
    viewModel: BackgroundAgentViewModel,
    focusPreset: AgentLlmProviderEntry? = null,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadLlmProviders() }
    var presetEntry by remember { mutableStateOf<AgentLlmProviderEntry?>(null) }
    LaunchedEffect(focusPreset) { if (focusPreset != null) presetEntry = focusPreset }
    var customCreateOpen by remember { mutableStateOf(false) }
    var customEditingRow by remember { mutableStateOf<AgentLlmSavedProvider?>(null) }
    var confirmDeleteRow by remember { mutableStateOf<AgentLlmSavedProvider?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI providers") },
        text = {
            LazyColumn(Modifier.height(440.dp)) {
                val official = state.llmCatalog?.official ?: emptyList()
                item { LlmGroupHeader("Official APIs") }
                if (official.isEmpty()) {
                    item {
                        Text(
                            "The provider catalog is not available while NEBians is offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                items(official, key = { "preset-${it.slug}" }) { entry ->
                    val savedRow = state.llmSavedProviders.firstOrNull { it.id.isNotBlank() && it.id == entry.byokProviderId }
                    PresetManageRow(entry = entry, savedRow = savedRow, onManage = { presetEntry = entry })
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Custom providers",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { customCreateOpen = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
                val customRows = state.llmSavedProviders.filter { it.provider == "custom" }
                if (customRows.isEmpty()) {
                    item {
                        Text(
                            "Point Zeus at any OpenAI, Anthropic or Gemini compatible endpoint — local models, gateways, proxies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                items(customRows, key = { "row-${it.id}" }) { row ->
                    CustomManageRow(
                        row = row,
                        onToggle = { enabled ->
                            viewModel.updateLlmProvider(row.id, mapOf("enabled" to enabled.toString()), null)
                        },
                        onEdit = { customEditingRow = row },
                        onDelete = { confirmDeleteRow = row }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    presetEntry?.let { entry ->
        val row = state.llmSavedProviders.firstOrNull { it.id.isNotBlank() && it.id == entry.byokProviderId }
        AgentPresetKeyDialog(
            entry = entry,
            row = row,
            busy = state.busy,
            onDismiss = { presetEntry = null },
            onSave = { apiKey, model, baseUrl ->
                val fields = buildMap {
                    if (apiKey.isNotBlank()) put("apiKey", apiKey.trim())
                    if (model.isNotBlank()) put("defaultModel", model.trim())
                    if (baseUrl.isNotBlank()) put("baseUrl", baseUrl.trim())
                }
                if (row == null) {
                    viewModel.saveLlmProvider(fields + ("provider" to entry.slug), emptyList()) { presetEntry = null }
                } else {
                    viewModel.updateLlmProvider(row.id, fields, null) { presetEntry = null }
                }
            },
            onTest = viewModel::testLlmProvider,
            onDelete = if (row == null) null else ({ confirmDeleteRow = row })
        )
    }

    if (customCreateOpen || customEditingRow != null) {
        val row = customEditingRow
        AgentCustomProviderDialog(
            row = row,
            busy = state.busy,
            onDismiss = { customCreateOpen = false; customEditingRow = null },
            onCreate = { fields, models ->
                viewModel.saveLlmProvider(fields, models) { customCreateOpen = false }
            },
            onUpdate = { fields, models ->
                if (row != null) {
                    viewModel.updateLlmProvider(row.id, fields, models) { customEditingRow = null }
                }
            },
            onTest = viewModel::testLlmProvider
        )
    }

    confirmDeleteRow?.let { row ->
        AgentConfirmDialog(
            title = "Remove provider?",
            body = if (row.provider == "custom") {
                "\"${row.name}\" and its API key will be permanently removed from this device account."
            } else {
                "Your saved ${row.provider} API key will be removed. Server-managed keys remain available."
            },
            destructive = true,
            confirmLabel = "Remove",
            onDismiss = { confirmDeleteRow = null },
            onConfirm = {
                confirmDeleteRow = null
                presetEntry = null
                viewModel.deleteLlmProvider(row.id)
            }
        )
    }
}

@Composable
private fun PresetManageRow(
    entry: AgentLlmProviderEntry,
    savedRow: AgentLlmSavedProvider?,
    onManage: () -> Unit
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.freeNote.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            entry.freeNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        },
        supportingContent = {
            val status = when {
                savedRow != null -> if (entry.keyMasked.isNotBlank()) "Your key ${entry.keyMasked}" else "Your key saved"
                entry.keySource == "env" -> "Using server key"
                entry.keySource == "bot" -> "Using shared workspace key"
                else -> "Not connected"
            }
            Text(status, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Icon(Icons.Rounded.Key, null) },
        trailingContent = { TextButton(onClick = onManage) { Text(if (savedRow != null) "Manage" else "Add key") } }
    )
    HorizontalDivider()
}

@Composable
private fun CustomManageRow(
    row: AgentLlmSavedProvider,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = { Text(row.name.ifBlank { "Custom provider" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val parts = listOf(row.baseUrl, "${row.models.size} model" + (if (row.models.size == 1) "" else "s"), row.keyMasked)
                .filter { it.isNotBlank() }
            Text(parts.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Icon(Icons.Rounded.Settings, null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = row.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit provider") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Remove provider", tint = MaterialTheme.colorScheme.error) }
            }
        }
    )
    HorizontalDivider()
}

// ---------------------------------------------------------------------------
// Preset BYOK key editor
// ---------------------------------------------------------------------------

@Composable
private fun AgentPresetKeyDialog(
    entry: AgentLlmProviderEntry,
    row: AgentLlmSavedProvider?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, defaultModel: String, baseUrl: String) -> Unit,
    onTest: (fields: Map<String, String>, onResult: (AgentLlmTestResponse) -> Unit) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(row?.defaultModel ?: "") }
    var baseUrl by remember { mutableStateOf(row?.baseUrl ?: "") }
    var modelMenu by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AgentLlmTestResponse?>(null) }
    val effectiveModel = model.ifBlank { entry.defaultModel }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry.freeNote.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            entry.freeNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    placeholder = {
                        Text(
                            if (row?.keyMasked?.isNotBlank() == true) "Saved: ${row.keyMasked} — type to replace"
                            else "Paste your API key"
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Box {
                    OutlinedButton(onClick = { modelMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            effectiveModel.ifBlank { "Choose default model" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        entry.models.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(option.displayLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (option.note.isNotBlank()) {
                                            Text(option.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = { model = option.id; modelMenu = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL (optional)") },
                    placeholder = { Text(entry.baseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                LlmTestResultLine(testing = testing, result = testResult)
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Remove saved key", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val fields = buildMap {
                            if (row != null && apiKey.isBlank()) {
                                put("providerId", row.id)
                            } else {
                                put("provider", entry.slug)
                                if (apiKey.isNotBlank()) put("apiKey", apiKey.trim())
                                put("model", effectiveModel)
                                if (baseUrl.isNotBlank()) put("baseUrl", baseUrl.trim())
                            }
                        }
                        if (!fields.containsKey("providerId") && apiKey.isBlank() && row == null) return@TextButton
                        testing = true
                        testResult = null
                        onTest(fields) { testing = false; testResult = it }
                    },
                    enabled = !testing && !busy && (row != null || apiKey.isNotBlank())
                ) { Text("Test") }
                Button(
                    onClick = { onSave(apiKey, model.let { if (it.isBlank()) effectiveModel else it }, baseUrl) },
                    enabled = !busy && (row != null || apiKey.isNotBlank())
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ---------------------------------------------------------------------------
// Custom provider editor
// ---------------------------------------------------------------------------

@Composable
private fun AgentCustomProviderDialog(
    row: AgentLlmSavedProvider?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (fields: Map<String, String>, models: List<String>) -> Unit,
    onUpdate: (fields: Map<String, String>, models: List<String>?) -> Unit,
    onTest: (fields: Map<String, String>, onResult: (AgentLlmTestResponse) -> Unit) -> Unit
) {
    val creating = row == null
    var name by remember { mutableStateOf(row?.name ?: "") }
    var apiFormat by remember { mutableStateOf(row?.apiFormat ?: "openai") }
    var baseUrl by remember { mutableStateOf(row?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf("") }
    var modelsCsv by remember { mutableStateOf(row?.models?.joinToString(", ") ?: "") }
    var defaultModel by remember { mutableStateOf(row?.defaultModel ?: "") }
    var ctxWindow by remember { mutableStateOf(row?.contextWindow?.toString() ?: "1000000") }
    var maxOut by remember { mutableStateOf(row?.maxOutputTokens?.toString() ?: "4096") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<AgentLlmTestResponse?>(null) }

    val models = modelsCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().take(40)
    val baseOk = baseUrl.isBlank() || baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
    val canSave = if (creating) {
        baseUrl.startsWith("http://") || baseUrl.startsWith("https://")
    } else baseOk
    val saveEnabled = !busy && canSave && (if (creating) models.isNotEmpty() else true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Add custom provider" else "Edit ${row?.name.orEmpty()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    placeholder = { Text("My provider") },
                    singleLine = true
                )
                Column {
                    Text("API format", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("openai" to "OpenAI", "anthropic" to "Anthropic", "gemini" to "Gemini").forEach { (value, label) ->
                            FilterChip(
                                selected = apiFormat == value,
                                onClick = { apiFormat = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    Text(
                        when (apiFormat) {
                            "anthropic" -> "Anthropic Messages API (x-api-key)"
                            "gemini" -> "Gemini generateContent API"
                            else -> "OpenAI-compatible /chat/completions"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    isError = !baseOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    placeholder = {
                        Text(if (row?.keyMasked?.isNotBlank() == true) "Saved: ${row.keyMasked} — type to replace" else "sk-...")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = modelsCsv,
                    onValueChange = { modelsCsv = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Models (comma separated)") },
                    placeholder = { Text("gpt-5.4-mini, claude-sonnet-5") },
                    minLines = 2,
                    maxLines = 3
                )
                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default model (optional)") },
                    placeholder = { Text(models.firstOrNull() ?: "first model is used") },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ctxWindow,
                        onValueChange = { ctxWindow = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Context") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = maxOut,
                        onValueChange = { maxOut = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Max output") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                LlmTestResultLine(testing = testing, result = testResult)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        val fields = buildMap {
                            if (!creating && apiKey.isBlank() && row != null) {
                                put("providerId", row.id)
                            } else {
                                put("provider", "custom")
                                put("apiFormat", apiFormat)
                                if (baseUrl.isNotBlank()) put("baseUrl", baseUrl.trim())
                                if (apiKey.isNotBlank()) put("apiKey", apiKey.trim())
                                put("model", defaultModel.ifBlank { models.firstOrNull().orEmpty() })
                            }
                        }
                        testing = true
                        testResult = null
                        onTest(fields) { testing = false; testResult = it }
                    },
                    enabled = !testing && !busy && baseOk && (creating && canSave || !creating)
                ) { Text("Test") }
                Button(
                    onClick = {
                        val fields = buildMap {
                            put("provider", "custom")
                            if (name.isNotBlank()) put("name", name.trim())
                            put("apiFormat", apiFormat)
                            if (baseUrl.isNotBlank()) put("baseUrl", baseUrl.trim())
                            if (apiKey.isNotBlank()) put("apiKey", apiKey.trim())
                            if (defaultModel.isNotBlank()) put("defaultModel", defaultModel.trim())
                            ctxWindow.toIntOrNull()?.let { put("contextWindow", it.toString()) }
                            maxOut.toIntOrNull()?.let { put("maxOutputTokens", it.toString()) }
                        }
                        if (creating) onCreate(fields, models) else onUpdate(fields, models.ifEmpty { null })
                    },
                    enabled = saveEnabled
                ) { Text(if (creating) "Add provider" else "Save changes") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LlmTestResultLine(testing: Boolean, result: AgentLlmTestResponse?) {
    when {
        testing -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Testing connection...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        result != null -> Text(
            if (result.ok) {
                "Connected to ${result.model.ifBlank { "provider" }} in ${result.latencyMs} ms."
            } else {
                "Test failed: ${result.error ?: "HTTP ${result.status}"}"
            },
            color = if (result.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
