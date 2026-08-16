package com.zeus.code.ui.browser

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zeus.code.browser.BrowserAgentRunner
import com.zeus.code.browser.BrowserController
import com.zeus.code.browser.BrowserPageContent
import com.zeus.code.browser.DomElementInfo
import com.zeus.code.browser.WebAgentMessage
import com.zeus.code.ui.agent.AgentModelPickerDialog
import com.zeus.code.ui.agent.BackgroundAgentViewModel
import com.zeus.code.ui.agent.MarkdownContent
import kotlinx.coroutines.launch

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
    val thinkingMode by runner.thinkingMode.collectAsState()
    val webSearch by runner.webSearch.collectAsState()
    val agentState by agentViewModel.state.collectAsState()

    val currentUrl by browserController.currentUrl.collectAsState()
    val pageTitle by browserController.pageTitle.collectAsState()
    val isLoadingPage by browserController.isLoading.collectAsState()
    val canGoBack by browserController.canGoBack.collectAsState()
    val canGoForward by browserController.canGoForward.collectAsState()
    val isDesktopMode by browserController.isDesktopMode.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var isEditingAddress by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showThinkingMenu by remember { mutableStateOf(false) }
    var extractedContent by remember { mutableStateOf<BrowserPageContent?>(null) }
    var showInspectorSheet by remember { mutableStateOf(false) }
    var elementFilterType by remember { mutableStateOf("ALL") }

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

    LaunchedEffect(currentUrl) {
        if (!isEditingAddress) {
            addressInput = currentUrl
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    AssistChip(
                        onClick = { showModelPicker = true },
                        label = { Text("Model", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    if (selectedTab == 0) {
                        IconButton(onClick = { runner.clearMessages() }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Chat")
                        }
                    }
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
            // Tab Selector (Chat vs Browser)
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Chat, contentDescription = null) },
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
                            Icon(Icons.Rounded.Language, contentDescription = null)
                        }
                    },
                    text = { Text("Live Browser") }
                )
            }

            if (selectedTab == 0) {
                // ==================== TAB 1: CHAT INTERFACE ====================
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { showModelPicker = true },
                                label = { Text(currentModelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp)) }
                            )
                            Box {
                                FilterChip(
                                    selected = thinkingMode != "disabled",
                                    onClick = { showThinkingMenu = true },
                                    label = {
                                        Text(
                                            when (thinkingMode) {
                                                "enabled" -> "Thinking: On"
                                                "disabled" -> "Thinking: Off"
                                                else -> "Thinking: Auto"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Psychology, null, Modifier.size(16.dp)) },
                                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(16.dp)) }
                                )
                                DropdownMenu(
                                    expanded = showThinkingMenu,
                                    onDismissRequest = { showThinkingMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Auto (Recommended for Qwen)") },
                                        leadingIcon = {
                                            if (thinkingMode == "auto") Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = {
                                            runner.setThinkingMode("auto")
                                            showThinkingMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Thinking Enabled (Deep CoT)") },
                                        leadingIcon = {
                                            if (thinkingMode == "enabled") Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = {
                                            runner.setThinkingMode("enabled")
                                            showThinkingMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Thinking Disabled (Fast)") },
                                        leadingIcon = {
                                            if (thinkingMode == "disabled") Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp))
                                        },
                                        onClick = {
                                            runner.setThinkingMode("disabled")
                                            showThinkingMenu = false
                                        }
                                    )
                                }
                            }
                            FilterChip(
                                selected = webSearch,
                                onClick = { runner.toggleWebSearch() },
                                label = { Text(if (webSearch) "Web Search: On" else "Web Search: Off", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(16.dp)) }
                            )
                        }
                    }

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
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { runner.stop() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.TravelExplore,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "Autonomous Web Agent",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Give your agent any web goal. It navigates sites, clicks interactive elements, submits forms, and collects answers automatically.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(20.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SuggestionChip(
                                        onClick = { inputPrompt = "Search Wikipedia for Quantum Computing and summarize the main points" },
                                        label = { Text("Quantum Computing Summary", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    SuggestionChip(
                                        onClick = { inputPrompt = "Search HackerNews for today's top tech news" },
                                        label = { Text("Top HackerNews Tech News", style = MaterialTheme.typography.labelSmall) }
                                    )
                                    SuggestionChip(
                                        onClick = { inputPrompt = "Search GitHub for trending Kotlin mobile projects" },
                                        label = { Text("Trending GitHub Kotlin Repos", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                AgentMessageBubble(message = msg)
                            }
                        }
                    }

                    // Chat Bottom Input Bar
                    Surface(
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputPrompt,
                                onValueChange = { inputPrompt = it },
                                placeholder = { Text("Ask the Web Agent to browse or search...") },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                enabled = !isRunning
                            )
                            Spacer(Modifier.width(8.dp))
                            if (isRunning) {
                                IconButton(
                                    onClick = { runner.stop() },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Rounded.Stop, contentDescription = "Stop")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        val text = inputPrompt.trim()
                                        if (text.isNotBlank()) {
                                            inputPrompt = ""
                                            val sel = agentState.llmSelection
                                            val provider = if (sel.isDefault) "qwen" else sel.provider
                                            val model = if (sel.isDefault) "" else sel.model
                                            runner.startTask(
                                                goal = text,
                                                provider = provider,
                                                model = model,
                                                providerId = sel.providerId
                                            )
                                        }
                                    },
                                    enabled = inputPrompt.isNotBlank(),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                                }
                            }
                        }
                    }
                }
            } else {
                // ==================== TAB 2: LIVE FULL-BLEED BROWSER ====================
                Column(modifier = Modifier.fillMaxSize()) {
                    // Browser Top Address Bar & Toolbar
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { scope.launch { browserController.goBack() } },
                                    enabled = canGoBack,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { scope.launch { browserController.goForward() } },
                                    enabled = canGoForward,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { scope.launch { browserController.reload() } },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        BasicTextField(
                                            value = addressInput,
                                            onValueChange = {
                                                addressInput = it
                                                isEditingAddress = true
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isEditingAddress) {
                                            IconButton(
                                                onClick = {
                                                    isEditingAddress = false
                                                    scope.launch { browserController.navigate(addressInput) }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Go", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { browserController.toggleDesktopMode() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isDesktopMode) Icons.Rounded.DesktopMac else Icons.Rounded.PhoneAndroid,
                                        contentDescription = "Toggle Desktop Mode",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            extractedContent = browserController.extractPageContent()
                                            showInspectorSheet = true
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.FindInPage, contentDescription = "DOM Inspector", modifier = Modifier.size(18.dp))
                                }
                            }
                            if (isLoadingPage) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
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
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1
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
        val allElements = extractedContent?.elements.orEmpty()
        val filtered = when (elementFilterType) {
            "BUTTONS" -> allElements.filter { it.tagName == "button" || it.inputType == "button" || it.inputType == "submit" }
            "LINKS" -> allElements.filter { it.tagName == "a" || it.href.isNotBlank() }
            "INPUTS" -> allElements.filter { it.tagName == "input" || it.tagName == "textarea" || it.tagName == "select" }
            else -> allElements
        }

        ModalBottomSheet(onDismissRequest = { showInspectorSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("DOM Inspector (${allElements.size} elements)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = elementFilterType == "ALL", onClick = { elementFilterType = "ALL" }, label = { Text("All (${allElements.size})") })
                    FilterChip(selected = elementFilterType == "BUTTONS", onClick = { elementFilterType = "BUTTONS" }, label = { Text("Buttons") })
                    FilterChip(selected = elementFilterType == "LINKS", onClick = { elementFilterType = "LINKS" }, label = { Text("Links") })
                    FilterChip(selected = elementFilterType == "INPUTS", onClick = { elementFilterType = "INPUTS" }, label = { Text("Inputs") })
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.zeusId.ifBlank { it.selector } }) { el ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text("[${el.zeusId}]", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Text("<${el.tagName}>", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (el.text.isNotBlank()) Text(el.text, style = MaterialTheme.typography.bodySmall)
                                    if (el.placeholder.isNotBlank()) Text("Placeholder: ${el.placeholder}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (el.href.isNotBlank()) Text("Href: ${el.href}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            browserController.clickElement(el.zeusId)
                                            showInspectorSheet = false
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Click", style = MaterialTheme.typography.labelSmall)
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                message.actionName.uppercase(),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (message.actionTarget.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                message.actionTarget,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (message.thought.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            message.thought,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
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
                        Spacer(Modifier.height(6.dp))
                    }
                    if (isUser) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        MarkdownContent(
                            markdown = message.content,
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
