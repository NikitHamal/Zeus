package com.zeus.code.ui.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.code.automation.InstalledApp
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
    val isPaused by phoneAgentRunner.isPaused.collectAsState()
    val currentStatus by phoneAgentRunner.currentStatus.collectAsState()
    val messages by phoneAgentRunner.messages.collectAsState()
    val agentState by agentViewModel.state.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var promptText by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }

    // Screen Inspector & Manual tools state
    var inspectedNodes by remember { mutableStateOf<List<UiElementNode>>(emptyList()) }
    var inspectedPackage by remember { mutableStateOf("") }
    var inspectedActivity by remember { mutableStateOf("") }
    var appSearchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }

    var manualTapX by remember { mutableStateOf("540") }
    var manualTapY by remember { mutableStateOf("960") }
    var manualTypeText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

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
        installedApps = phoneController.getInstalledApps()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Phone Controller Agent", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                text = if (isServiceActive) "Accessibility Automation Ready" else "Accessibility Inactive (Tap to Enable)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    if (!isServiceActive) phoneController.openAccessibilitySettings()
                                }
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
                        IconButton(onClick = {
                            phoneController.checkServiceState()
                            installedApps = phoneController.getInstalledApps()
                        }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Status")
                        }
                        if (selectedTabIndex == 0) {
                            IconButton(onClick = { phoneAgentRunner.clearMessages() }) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Chat")
                            }
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
                        text = { Text("Screen & Controls") },
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
                    // ==================== TAB 1: AGENT CHAT ====================
                    Column(Modifier.fillMaxSize()) {
                        // Model Selector & Service State Strip
                        Surface(
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { showModelPicker = true },
                                    label = { Text(currentModelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp)) }
                                )
                                Spacer(Modifier.weight(1f))
                                if (!isServiceActive) {
                                    FilledTonalButton(
                                        onClick = { phoneController.openAccessibilitySettings() },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Enable Service", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        // Running status banner
                        AnimatedVisibility(visible = isRunning) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = currentStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { phoneAgentRunner.togglePause() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                            contentDescription = if (isPaused) "Resume" else "Pause",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { phoneAgentRunner.stop() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // Message Feed
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            contentPadding = PaddingValues(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                PhoneChatBubble(msg)
                            }
                        }

                        // Suggestion Chips (when idle)
                        if (!isRunning && messages.size <= 2) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                            ) {
                                Text("Suggested Tasks:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SuggestionChip(
                                        onClick = { promptText = "Open Settings and check available device storage" },
                                        label = { Text("Check storage", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    SuggestionChip(
                                        onClick = { promptText = "Open YouTube and search for Lo-Fi music" },
                                        label = { Text("Search YouTube", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    SuggestionChip(
                                        onClick = { promptText = "Open Douyin / TikTok and scroll 5 videos" },
                                        label = { Text("Auto-scroll feed", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
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
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = promptText,
                                    onValueChange = { promptText = it },
                                    placeholder = { Text("Tell Phone Agent what to do...") },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 3,
                                    enabled = !isRunning
                                )
                                Spacer(Modifier.width(8.dp))
                                if (isRunning) {
                                    IconButton(
                                        onClick = { phoneAgentRunner.stop() },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Rounded.Stop, contentDescription = "Stop")
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val text = promptText.trim()
                                            if (text.isNotBlank()) {
                                                promptText = ""
                                                val sel = agentState.llmSelection
                                                val provider = if (sel.isDefault) "qwen" else sel.provider
                                                val model = if (sel.isDefault) "" else sel.model
                                                phoneAgentRunner.startTask(
                                                    instruction = text,
                                                    provider = provider,
                                                    model = model,
                                                    providerId = sel.providerId
                                                )
                                            }
                                        },
                                        enabled = promptText.isNotBlank(),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // ==================== TAB 2: SCREEN INSPECTOR & CONTROLS ====================
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Quick Device Navigation Buttons
                        item {
                            Text("System Quick Navigation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { scope.launch { phoneController.pressHome() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Home", style = MaterialTheme.typography.labelSmall)
                                }
                                FilledTonalButton(
                                    onClick = { scope.launch { phoneController.pressBack() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Back", style = MaterialTheme.typography.labelSmall)
                                }
                                FilledTonalButton(
                                    onClick = { scope.launch { phoneController.pressRecents() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Recents", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { scope.launch { phoneController.openNotifications() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Notifs", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { scope.launch { phoneController.openQuickSettings() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.SettingsSuggest, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Quick Set", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { scope.launch { phoneController.takeScreenshot() } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Capture", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        // Scroll presets
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Quick Gestures & Scroll", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = { scope.launch { phoneController.scrollDown() } },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.ArrowDownward, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Scroll Down", style = MaterialTheme.typography.labelSmall)
                                        }
                                        FilledTonalButton(
                                            onClick = { scope.launch { phoneController.scrollUp() } },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.ArrowUpward, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Scroll Up", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Manual Tap & Type Pad
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Manual Tap & Text Dispatch", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = manualTapX,
                                            onValueChange = { manualTapX = it },
                                            label = { Text("X") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = manualTapY,
                                            onValueChange = { manualTapY = it },
                                            label = { Text("Y") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                val x = manualTapX.toFloatOrNull() ?: 540f
                                                val y = manualTapY.toFloatOrNull() ?: 960f
                                                scope.launch { phoneController.tapCoordinates(x, y) }
                                            }
                                        ) {
                                            Text("Tap")
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = manualTypeText,
                                            onValueChange = { manualTypeText = it },
                                            placeholder = { Text("Text to type...") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            onClick = {
                                                if (manualTypeText.isNotBlank()) {
                                                    val t = manualTypeText
                                                    manualTypeText = ""
                                                    scope.launch { phoneController.inputText(t) }
                                                }
                                            }
                                        ) {
                                            Text("Type")
                                        }
                                    }
                                }
                            }
                        }

                        // Screen Inspector Trigger
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Live Screen Inspector", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val dump = phoneController.dumpScreenHierarchy()
                                            inspectedNodes = dump.nodes
                                            inspectedPackage = dump.packageName
                                            inspectedActivity = dump.activityName
                                        }
                                    }
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Inspect Now")
                                }
                            }
                        }

                        if (inspectedPackage.isNotBlank()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("Active App: $inspectedPackage", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text("Activity: $inspectedActivity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Total Elements: ${inspectedNodes.size}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        if (inspectedNodes.isNotEmpty()) {
                            items(inspectedNodes.take(30), key = { it.index }) { node ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text("[${node.index}]", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    node.className.substringAfterLast("."),
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (node.isClickable) {
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("(clickable)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                                }
                                            }
                                            if (node.text.isNotBlank()) Text("Text: \"${node.text}\"", style = MaterialTheme.typography.bodyMedium)
                                            if (node.contentDescription.isNotBlank()) Text("Desc: \"${node.contentDescription}\"", style = MaterialTheme.typography.bodySmall)
                                            Text("Center: (${node.centerX}, ${node.centerY})", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                        }
                                        FilledTonalButton(
                                            onClick = {
                                                scope.launch { phoneController.clickElementByIndex(node.index) }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Tap", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // App Launcher
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Installed App Launcher", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = appSearchQuery,
                                        onValueChange = { appSearchQuery = it },
                                        placeholder = { Text("Search installed apps (e.g. YouTube, Settings)...") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))

                                    val filteredApps = installedApps.filter {
                                        appSearchQuery.isBlank() ||
                                            it.name.contains(appSearchQuery, ignoreCase = true) ||
                                            it.packageName.contains(appSearchQuery, ignoreCase = true)
                                    }.take(10)

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        filteredApps.forEach { app ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { phoneController.launchApp(app.packageName) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(app.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    FilledTonalButton(
                                                        onClick = { phoneController.launchApp(app.packageName) },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                    ) {
                                                        Text("Launch", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Activity Logs
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Activity Log (${logs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (logs.isNotEmpty()) {
                                    TextButton(onClick = { phoneController.clearLogs() }) {
                                        Text("Clear")
                                    }
                                }
                            }
                        }
                        if (logs.isEmpty()) {
                            item {
                                Text("No actions dispatched yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(logs.reversed().take(20)) { log ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (log.isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                        ) {
                                            Text(
                                                log.action,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (log.isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(log.details, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
    val isAction = message.sender == "action"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                shape = CircleShape,
                color = when {
                    isSystem -> MaterialTheme.colorScheme.errorContainer
                    isAction -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    when {
                        isSystem -> Icons.Rounded.Warning
                        isAction -> Icons.Rounded.TouchApp
                        else -> Icons.Rounded.SmartToy
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(5.dp),
                    tint = when {
                        isSystem -> MaterialTheme.colorScheme.onErrorContainer
                        isAction -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
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
                isAction -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.thought.isNotBlank() && !isUser) {
                    Text(
                        "Thought: ${message.thought}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (message.actionType != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            "Action: ${message.actionType.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
