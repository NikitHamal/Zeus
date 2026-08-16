package com.zeus.code.ui.browser

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zeus.code.browser.BrowserAgentRunner
import com.zeus.code.browser.BrowserController
import com.zeus.code.browser.BrowserPageContent
import com.zeus.code.browser.WebAgentMessage
import kotlinx.coroutines.launch

import com.zeus.code.ui.agent.AgentModelPickerDialog
import com.zeus.code.ui.agent.BackgroundAgentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebAgentScreen(
    browserController: BrowserController,
    runner: BrowserAgentRunner,
    agentViewModel: BackgroundAgentViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Chat, 1 = Browser
    val isRunning by runner.isRunning.collectAsState()
    val messages by runner.messages.collectAsState()
    val currentStatus by runner.currentStatus.collectAsState()
    val agentState by agentViewModel.state.collectAsState()

    val currentUrl by browserController.currentUrl.collectAsState()
    val pageTitle by browserController.pageTitle.collectAsState()
    val isLoadingPage by browserController.isLoading.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var showModelPicker by remember { mutableStateOf(false) }
    var extractedContent by remember { mutableStateOf<BrowserPageContent?>(null) }
    var showInspectorSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Autonomous Web Agent",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentModelLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Unified Model Picker Button
                    AssistChip(
                        onClick = { showModelPicker = true },
                        label = { Text("Model", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Clean Top Tab Selector (Chat vs Browser)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                    text = { Text("Agent Chat") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(badge = {
                            if (isRunning) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("Active") }
                            }
                        }) {
                            Icon(Icons.Default.Language, contentDescription = null)
                        }
                    },
                    text = { Text("Live Browser") }
                )
            }

            if (selectedTab == 0) {
                // ==================== TAB 1: CHAT INTERFACE ====================
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isRunning) {
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
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.TravelExplore,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Autonomous Web Agent",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Chat with the AI model. It will inspect web pages, click elements, fill forms, and deliver answers in real-time.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                AgentMessageBubble(message = msg)
                            }
                        }
                    }

                    // Chat Bottom Input Bar
                    Surface(
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputPrompt,
                                onValueChange = { inputPrompt = it },
                                placeholder = { Text("Ask or give a web task...") },
                                modifier = Modifier.weight(1f),
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                                if (!isRunning) {
                                    FilledIconButton(
                                        onClick = {
                                            val p = inputPrompt.trim()
                                            inputPrompt = ""
                                            val sel = agentState.llmSelection
                                            val provider = if (sel.isDefault) "motiftech" else sel.provider
                                            val model = if (sel.isDefault) "motif-102b" else sel.model
                                            runner.startTask(
                                                goal = p,
                                                provider = provider,
                                                model = model,
                                                providerId = sel.providerId
                                            )
                                        },
                                        enabled = inputPrompt.isNotBlank()
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send")
                                    }
                                } else {
                                    IconButton(
                                        onClick = { runner.stop() },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                                    }
                                }
                        }
                    }
                }
            } else {
                // ==================== TAB 2: LIVE FULL-BLEED BROWSER ====================
                Column(modifier = Modifier.fillMaxSize()) {
                    // Minimal Address & Action Header
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        extractedContent = browserController.extractPageContent()
                                        showInspectorSheet = true
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.FindInPage, contentDescription = "DOM Inspector", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (isLoadingPage) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    // Full Bleed WebView
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    ) {
                        AndroidView(
                            factory = {
                                val wv = browserController.webView
                                if (wv?.parent != null) {
                                    (wv.parent as? ViewGroup)?.removeView(wv)
                                }
                                wv ?: android.webkit.WebView(it)
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Action Banner (When agent is active)
                        if (isRunning) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = currentStatus,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInspectorSheet && extractedContent != null) {
        ModalBottomSheet(onDismissRequest = { showInspectorSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("Interactive DOM Elements (${extractedContent?.elements?.size ?: 0})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(extractedContent?.elements ?: emptyList()) { el ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("<${el.tagName}> ${el.selector}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                    if (el.text.isNotBlank()) Text(el.text, style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            browserController.clickElement(el.selector)
                                            showInspectorSheet = false
                                        }
                                    }
                                ) {
                                    Text("Click")
                                }
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

@Composable
fun AgentMessageBubble(message: WebAgentMessage) {
    val isUser = message.role == "user"
    val isAction = message.role == "action"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isAction) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "[${message.actionName.uppercase()}] ${message.actionTarget} → ${message.content}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isUser && message.thought.isNotBlank()) {
                        Text(
                            text = "Thought: ${message.thought}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
