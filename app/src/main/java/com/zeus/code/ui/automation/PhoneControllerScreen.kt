package com.zeus.code.ui.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.code.automation.PhoneAgentRunner
import com.zeus.code.automation.PhoneChatMessage
import com.zeus.code.automation.PhoneController
import com.zeus.code.automation.UiElementNode
import com.zeus.code.ui.agent.AgentModelPickerDialog
import com.zeus.code.ui.agent.BackgroundAgentViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneControllerScreen(
    phoneController: PhoneController,
    phoneAgentRunner: PhoneAgentRunner,
    agentViewModel: BackgroundAgentViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isServiceActive by phoneController.isServiceActive.collectAsState()
    val logs by phoneController.logs.collectAsState()
    val isRunning by phoneAgentRunner.isRunning.collectAsState()
    val messages by phoneAgentRunner.messages.collectAsState()
    val agentState by agentViewModel.state.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var promptText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var inspectedNodes by remember { mutableStateOf<List<UiElementNode>>(emptyList()) }
    var targetPackage by remember { mutableStateOf("com.android.settings") }
    val scope = rememberCoroutineScope()

    val currentModelLabel = remember(agentState.llmSelection, agentState.llmCatalog) {
        val sel = agentState.llmSelection
        if (sel.isDefault) {
            val defaultEntry = agentState.llmCatalog?.community?.firstOrNull { it.selectableForAgent }
            defaultEntry?.models?.firstOrNull { it.id == defaultEntry.defaultModel }?.displayLabel
                ?: defaultEntry?.label
                ?: "Motif 3 (Default)"
        } else {
            val entry = (agentState.llmCatalog?.community.orEmpty() + agentState.llmCatalog?.official.orEmpty() + agentState.llmCatalog?.custom.orEmpty())
                .firstOrNull { it.slug == sel.provider || it.id == sel.providerId }
            val modelLabel = entry?.models?.firstOrNull { it.id == sel.model }?.displayLabel ?: sel.model
            "${entry?.label ?: sel.provider} · $modelLabel"
        }
    }

    LaunchedEffect(Unit) {
        phoneController.checkServiceState()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Phone Controller Agent", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                text = if (isServiceActive) "Live Accessibility Overlay Ready" else "Accessibility Inactive",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { phoneController.checkServiceState() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Status")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Agent Chat & Task") },
                        icon = { Icon(Icons.Rounded.SmartToy, null) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Screen & Logs") },
                        icon = { Icon(Icons.Rounded.PhoneAndroid, null) }
                    )
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    Column(Modifier.fillMaxSize()) {
                        // Model Selector Chip Bar & Permission Warning
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { showModelPicker = true },
                                label = { Text(currentModelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                            )
                            Spacer(Modifier.weight(1f))
                            if (!isServiceActive) {
                                TextButton(onClick = { phoneController.openAccessibilitySettings() }) {
                                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Enable Service", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Message Feed
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                PhoneChatBubble(msg)
                            }
                        }

                        // Suggestion Chips (when idle)
                        if (!isRunning && messages.size <= 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SuggestionChip(
                                    onClick = { promptText = "Open Settings and check available device storage" },
                                    label = { Text("Check storage", style = MaterialTheme.typography.labelSmall) }
                                )
                                SuggestionChip(
                                    onClick = { promptText = "Open Douyin / TikTok and auto-scroll 5 videos" },
                                    label = { Text("Auto-scroll feed", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        // Bottom Input Bar
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 3.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = promptText,
                                    onValueChange = { promptText = it },
                                    placeholder = { Text("Tell Phone Agent what to do live...") },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 4,
                                    shape = RoundedCornerShape(20.dp),
                                    enabled = !isRunning
                                )
                                Spacer(Modifier.width(8.dp))
                                if (isRunning) {
                                    FilledIconButton(
                                        onClick = { phoneAgentRunner.stop() },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Rounded.Stop, "Stop")
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (promptText.isNotBlank()) {
                                                val goal = promptText.trim()
                                                promptText = ""
                                                val sel = agentState.llmSelection
                                                val provider = if (sel.isDefault) "motiftech" else sel.provider
                                                val model = if (sel.isDefault) "motif-102b" else sel.model
                                                phoneAgentRunner.startTask(
                                                    instruction = goal,
                                                    provider = provider,
                                                    model = model,
                                                    providerId = sel.providerId
                                                )
                                            }
                                        },
                                        enabled = promptText.isNotBlank()
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Screen Inspector & Gesture Debugger
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text("Real Phone Gesture Controls", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { scope.launch { phoneController.pressHome() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Home")
                                }
                                FilledTonalButton(
                                    onClick = { scope.launch { phoneController.pressBack() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Back")
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            inspectedNodes = phoneController.dumpScreenNodes()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Inspect")
                                }
                            }
                        }

                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Launch Android App", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = targetPackage,
                                            onValueChange = { targetPackage = it },
                                            placeholder = { Text("e.g. com.android.settings") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Button(onClick = { phoneController.launchApp(targetPackage) }) {
                                            Text("Open")
                                        }
                                    }
                                }
                            }
                        }

                        if (inspectedNodes.isNotEmpty()) {
                            item {
                                Text("Captured Screen Elements (${inspectedNodes.size})", fontWeight = FontWeight.Bold)
                            }
                            items(inspectedNodes.take(25)) { node ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            node.className.substringAfterLast("."),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (node.text.isNotBlank()) Text("Text: \"${node.text}\"", style = MaterialTheme.typography.bodyMedium)
                                        if (node.contentDescription.isNotBlank()) Text("Desc: \"${node.contentDescription}\"", style = MaterialTheme.typography.bodySmall)
                                        Text("Bounds: ${node.bounds.toShortString()}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        item {
                            Text("Activity Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                        if (logs.isEmpty()) {
                            item {
                                Text("No actions dispatched yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(logs.reversed()) { log ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(log.action, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(log.details, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showModelPicker) {
                AgentModelPickerDialog(
                    state = agentState,
                    onDismiss = { showModelPicker = false },
                    onSelectDefault = {
                        agentViewModel.selectLlm(null)
                        showModelPicker = false
                    },
                    onSelect = { entry, modelId ->
                        agentViewModel.selectLlm(entry, modelId)
                        showModelPicker = false
                    },
                    onManageProviders = {
                        showModelPicker = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PhoneChatBubble(message: PhoneChatMessage) {
    val isUser = message.sender == "user"
    val isSystem = message.sender == "system"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = if (isSystem) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (isSystem) Icons.Rounded.Warning else Icons.Rounded.SmartToy,
                    contentDescription = null,
                    modifier = Modifier.padding(5.dp),
                    tint = if (isSystem) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = when {
                isUser -> MaterialTheme.colorScheme.primary
                isSystem -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                message.actionType != null -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.actionType != null) {
                    Text(
                        "Action: ${message.actionType}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
