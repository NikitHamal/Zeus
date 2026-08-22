@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zeus.code.ui.local

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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeus.code.local.LocalCustomProvider
import com.zeus.code.local.LocalModelChoice
import com.zeus.code.local.LocalProviderStore
import com.zeus.code.local.LocalSource
import com.zeus.code.model.AgentLlmProviderEntry

/* ------------------------------------------------------------------------- */
/* Model picker                                                              */
/* ------------------------------------------------------------------------- */

@Composable
fun LocalModelPickerDialog(
    state: LocalAgentUiState,
    onDismiss: () -> Unit,
    onSelect: (LocalModelChoice) -> Unit,
    onManageProviders: () -> Unit
) {
    var expanded by remember { mutableStateOf(setOf<String>()) }
    fun toggle(key: String) {
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose model") },
        text = {
            LazyColumn(Modifier.height(460.dp)) {

                // ---- OpenCode Zen ---------------------------------------
                item(key = "header-zen") {
                    Text(
                        "OpenCode Zen · free coding models",
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item(key = "zen") {
                    val configured = state.zenKeyMasked.isNotBlank()
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("OpenCode Zen", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text(
                                        "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                if (configured) "Your key ${state.zenKeyMasked}" else "Add your free key from opencode.ai/auth",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            if (!configured) TextButton(onClick = onManageProviders) { Text("Add key") }
                            else Icon(
                                if ("zen" in expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                                null
                            )
                        },
                        modifier = Modifier.clickable(enabled = configured) { toggle("zen") }
                    )
                    if (configured && "zen" in expanded) {
                        LocalProviderStore.ZEN_FALLBACK_MODELS.forEach { model ->
                            ModelChoiceRow(
                                title = model,
                                note = "Zen · free tier",
                                selected = state.selection.matches(
                                    LocalModelChoice(source = LocalSource.ZEN, model = model)
                                ),
                                onClick = {
                                    onSelect(
                                        LocalModelChoice(
                                            source = LocalSource.ZEN,
                                            model = model,
                                            label = "OpenCode Zen · $model"
                                        )
                                    )
                                }
                            )
                        }
                    }
                    HorizontalDivider()
                }

                // ---- NEBians --------------------------------------------
                val catalog = state.nebiansCatalog
                if (catalog != null) {
                    item(key = "header-neb") {
                        Text(
                            "NEBians · ${if (state.nebiansConnected) "your connected account" else "not connected"}",
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    catalog.selectableEntries().forEach { entry ->
                        val key = "neb-${entry.slug}-${entry.id.ifBlank { entry.byokProviderId }}"
                        item(key = key) {
                            NebiansEntryRow(
                                entry = entry,
                                expanded = key in expanded,
                                selection = state.selection,
                                onToggle = { toggle(key) },
                                onPick = { modelId, modelLabel ->
                                    onSelect(
                                        LocalModelChoice(
                                            source = LocalSource.NEBIANS,
                                            model = modelId,
                                            label = "${entry.label} · $modelLabel",
                                            nebSlug = entry.slug,
                                            nebRowId = entry.id.ifBlank { entry.byokProviderId }
                                        )
                                    )
                                }
                            )
                        }
                    }
                } else {
                    item(key = "neb-empty") {
                        Text(
                            if (state.nebiansConnected) "Loading NEBians models…" else "Connect NEBians from the Agent tab to use its providers here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }

                // ---- Custom providers -----------------------------------
                item(key = "header-custom") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Your providers",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onManageProviders) { Text("Manage") }
                    }
                }
                if (state.customProviders.isEmpty()) {
                    item(key = "custom-empty") {
                        Text(
                            "Add any OpenAI-compatible endpoint — OpenRouter, Ollama, Groq, LM Studio, gateways.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(state.customProviders.filter { it.enabled }, key = { "custom-${it.id}" }) { config ->
                    Column {
                        ListItem(
                            headlineContent = {
                                Text(config.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text(config.baseUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Icon(
                                    if ("custom-${config.id}" in expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                                    null
                                )
                            },
                            modifier = Modifier.clickable { toggle("custom-${config.id}") }
                        )
                        if ("custom-${config.id}" in expanded) {
                            config.models.forEach { model ->
                                ModelChoiceRow(
                                    title = model,
                                    note = config.label,
                                    selected = state.selection.matches(
                                        LocalModelChoice(source = LocalSource.CUSTOM, model = model, customId = config.id)
                                    ),
                                    onClick = {
                                        onSelect(
                                            LocalModelChoice(
                                                source = LocalSource.CUSTOM,
                                                model = model,
                                                label = "${config.label} · $model",
                                                customId = config.id
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (state.selection.isValid) "Done" else "Close") }
        },
        dismissButton = {
            TextButton(onClick = onManageProviders) {
                Icon(Icons.Rounded.Settings, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("AI providers")
            }
        }
    )
}

@Composable
private fun NebiansEntryRow(
    entry: AgentLlmProviderEntry,
    expanded: Boolean,
    selection: LocalModelChoice,
    onToggle: () -> Unit,
    onPick: (modelId: String, modelLabel: String) -> Unit
) {
    val disabled = !entry.available && entry.byokProviderId.isBlank() && entry.keySource != "env" && entry.keySource != "bot"
    Column {
        ListItem(
            headlineContent = {
                Text(entry.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    when {
                        !entry.available && disabled -> "Needs an API key (add via NEBians)"
                        entry.keyMasked.isNotBlank() -> "Your key ${entry.keyMasked}"
                        entry.available -> "${entry.models.size} model" + if (entry.models.size == 1) "" else "s"
                        else -> "Unavailable"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Icon(
                    if (expanded && !disabled) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                    null
                )
            },
            modifier = Modifier.clickable(enabled = !disabled) { onToggle() }
        )
        if (expanded && !disabled) {
            val models = entry.models.ifEmpty {
                entry.defaultModel.takeIf { it.isNotBlank() }
                    ?.let { listOf(com.zeus.code.model.AgentLlmModel(id = it)) }
                    .orEmpty()
            }
            models.forEach { model ->
                ModelChoiceRow(
                    title = model.displayLabel,
                    note = null,
                    selected = selection.matches(
                        LocalModelChoice(
                            source = LocalSource.NEBIANS,
                            model = model.id,
                            nebSlug = entry.slug,
                            nebRowId = entry.id.ifBlank { entry.byokProviderId }
                        )
                    ),
                    onClick = { onPick(model.id, model.displayLabel) }
                )
            }
        }
    }
}

@Composable
private fun ModelChoiceRow(title: String, note: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Providers manager                                                         */
/* ------------------------------------------------------------------------- */

@Composable
fun LocalProvidersDialog(
    state: LocalAgentUiState,
    viewModel: LocalAgentViewModel,
    onDismiss: () -> Unit
) {
    var zenEditor by remember { mutableStateOf(false) }
    var customEditor by remember { mutableStateOf<LocalCustomProvider?>(null) }
    var customCreate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<LocalCustomProvider?>(null) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Local AI providers") },
        text = {
            LazyColumn(Modifier.height(430.dp)) {
                // --- Zen ---
                item(key = "zen-card") {
                    ListItem(
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("OpenCode Zen", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text(
                                        "FREE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        supportingContent = {
                            Text(
                                if (state.zenKeyMasked.isNotBlank()) "Key saved ${state.zenKeyMasked} · stored in Android Keystore"
                                else "Free coding models with a key from opencode.ai/auth",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { uriHandler.openUri(LocalProviderStore.ZEN_KEY_URL) }) {
                                    Icon(Icons.Rounded.OpenInBrowser, "Get a Zen key")
                                }
                                TextButton(onClick = { zenEditor = true }) {
                                    Text(if (state.zenKeyMasked.isNotBlank()) "Update" else "Add key")
                                }
                            }
                        }
                    )
                    if (state.zenKeyMasked.isNotBlank()) {
                        TextButton(
                            onClick = { viewModel.removeZenKey() },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text("Remove saved key", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }

                // --- Custom ---
                item(key = "custom-header") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Custom OpenAI-compatible endpoints",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { customCreate = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
                item(key = "custom-hint") {
                    Text(
                        "Keys stay on this device. Works with OpenRouter, Ollama, Groq, Together, LM Studio and any /v1 compatible gateway.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.customProviders.isEmpty()) {
                    item(key = "custom-none") {
                        Text(
                            "No custom providers yet.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                items(state.customProviders, key = { "row-${it.id}" }) { config ->
                    val keyMask = state.customKeyMasks[config.id].orEmpty()
                    ListItem(
                        headlineContent = {
                            Text(config.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            val parts = listOfNotNull(
                                config.baseUrl.takeIf { it.isNotBlank() },
                                "${config.models.size} model" + if (config.models.size == 1) "" else "s",
                                keyMask.takeIf { it.isNotBlank() } ?: "no key"
                            )
                            Text(parts.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = config.enabled,
                                    onCheckedChange = { viewModel.setCustomEnabled(config.id, it) }
                                )
                                IconButton(onClick = { customEditor = config }) {
                                    Icon(Icons.Rounded.Edit, "Edit provider")
                                }
                                IconButton(onClick = { confirmDelete = config }) {
                                    Icon(Icons.Rounded.Delete, "Remove provider", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )

    if (zenEditor) {
        ZenKeyDialog(
            currentMask = state.zenKeyMasked,
            busy = state.busy,
            viewModel = viewModel,
            onDismiss = { zenEditor = false }
        )
    }
    if (customCreate || customEditor != null) {
        CustomProviderDialog(
            editing = customEditor,
            busy = state.busy,
            viewModel = viewModel,
            onDismiss = {
                customCreate = false
                customEditor = null
            }
        )
    }
    confirmDelete?.let { config ->
        com.zeus.code.ui.agent.AgentConfirmDialog(
            title = "Remove ${config.label}?",
            body = "The saved API key for this provider will be deleted from this device.",
            destructive = true,
            confirmLabel = "Remove",
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                viewModel.deleteCustomProvider(config.id)
            }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Zen key editor                                                            */
/* ------------------------------------------------------------------------- */

@Composable
private fun ZenKeyDialog(
    currentMask: String,
    busy: Boolean,
    viewModel: LocalAgentViewModel,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val uriHandler = LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenCode Zen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "Free coding models (code-supernova, big-pickle and more). Create a free key at opencode.ai/auth — it is stored encrypted on this device only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key") },
                    placeholder = {
                        Text(
                            if (currentMask.isNotBlank()) "Saved: $currentMask — type to replace"
                            else "Paste your Zen key"
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                when {
                    testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Testing…", style = MaterialTheme.typography.bodySmall)
                    }
                    testResult != null -> Text(
                        testResult!!.second,
                        color = if (testResult!!.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(onClick = { uriHandler.openUri(LocalProviderStore.ZEN_KEY_URL) }) {
                    Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Get a free key")
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !busy && !testing && (key.isNotBlank() || currentMask.isNotBlank()),
                    onClick = {
                        testing = true
                        testResult = null
                        viewModel.testZenKey(key) { ok, detail ->
                            testing = false
                            testResult = ok to detail
                        }
                    }
                ) { Text("Test") }
                Button(
                    enabled = !busy && key.isNotBlank(),
                    onClick = { viewModel.saveZenKey(key) { onDismiss() } }
                ) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ------------------------------------------------------------------------- */
/* Custom provider editor                                                    */
/* ------------------------------------------------------------------------- */

@Composable
private fun CustomProviderDialog(
    editing: LocalCustomProvider?,
    busy: Boolean,
    viewModel: LocalAgentViewModel,
    onDismiss: () -> Unit
) {
    val creating = editing == null
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(editing?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var modelsCsv by remember { mutableStateOf(editing?.models?.joinToString(", ").orEmpty()) }
    var defaultModel by remember { mutableStateOf(editing?.defaultModel.orEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val baseOk = baseUrl.trim().startsWith("http://") || baseUrl.trim().startsWith("https://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Add custom provider" else "Edit ${editing?.label}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    placeholder = { Text("OpenRouter") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://openrouter.ai/api/v1") },
                    singleLine = true,
                    isError = baseUrl.isNotBlank() && !baseOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key${if (creating || editing?.id.isNullOrBlank()) "" else " (optional)"}") },
                    placeholder = { Text("sk-…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = modelsCsv,
                    onValueChange = { modelsCsv = it; testResult = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Models (comma separated)") },
                    placeholder = { Text("deepseek/deepseek-chat-v3:free, qwen/qwen-2.5-coder-32b-instruct") },
                    minLines = 2,
                    maxLines = 3
                )
                OutlinedTextField(
                    value = defaultModel,
                    onValueChange = { defaultModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Default model (optional)") },
                    placeholder = { Text("first model is used") },
                    singleLine = true
                )
                when {
                    testing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Testing…", style = MaterialTheme.typography.bodySmall)
                    }
                    testResult != null -> Text(
                        testResult!!.second,
                        color = if (testResult!!.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Box(Modifier.height(2.dp))
                OutlinedButton(
                    enabled = !busy && !testing && baseOk,
                    onClick = {
                        testing = true
                        testResult = null
                        viewModel.testCustomProvider(baseUrl, apiKey) { ok, detail ->
                            testing = false
                            testResult = ok to detail
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (baseOk) "Test connection" else "Enter a valid base URL") }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && baseOk && modelsCsv.split(',').any { it.isNotBlank() },
                onClick = {
                    viewModel.saveCustomProvider(
                        id = editing?.id,
                        name = name,
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        modelsCsv = modelsCsv,
                        defaultModel = defaultModel
                    ) { onDismiss() }
                }
            ) { Text(if (creating) "Add provider" else "Save changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
