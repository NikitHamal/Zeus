package com.zeus.code.ui.browser

import android.view.ViewGroup
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
import com.zeus.code.browser.DomElementInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserAgentScreen(
    browserController: BrowserController,
    runner: BrowserAgentRunner? = null,
    modifier: Modifier = Modifier
) {
    val currentUrl by browserController.currentUrl.collectAsState()
    val pageTitle by browserController.pageTitle.collectAsState()
    val isLoading by browserController.isLoading.collectAsState()

    val isAgentRunning = runner?.isRunning?.collectAsState()?.value ?: false
    val agentStatus = runner?.statusText?.collectAsState()?.value ?: "Idle"
    val agentSteps = runner?.steps?.collectAsState()?.value ?: emptyList()

    var urlInput by remember { mutableStateOf("https://github.com") }
    var goalInput by remember { mutableStateOf("") }
    var showGoalInput by remember { mutableStateOf(false) }
    var showStepsSheet by remember { mutableStateOf(false) }
    var extractedContent by remember { mutableStateOf<BrowserPageContent?>(null) }
    var showInspectorSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (pageTitle.isNotBlank()) pageTitle else "Autonomous Browser Agent",
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAgentRunning) agentStatus else currentUrl,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAgentRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (runner != null) {
                                showGoalInput = !showGoalInput
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "Autonomous Task",
                            tint = if (isAgentRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                extractedContent = browserController.extractPageContent()
                                showInspectorSheet = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.FindInPage, contentDescription = "Inspect DOM")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                browserController.navigate(urlInput)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
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
            // Autonomous Agent Task Banner / Input
            if (showGoalInput || isAgentRunning) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "On-Device Autonomous Browser Loop",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = goalInput,
                                onValueChange = { goalInput = it },
                                placeholder = { Text("e.g. Find trending Kotlin repos and summarize") },
                                modifier = Modifier.weight(1f),
                                enabled = !isAgentRunning,
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            if (!isAgentRunning) {
                                Button(
                                    onClick = { runner?.startTask(goalInput) },
                                    enabled = goalInput.isNotBlank()
                                ) {
                                    Text("Run")
                                }
                            } else {
                                Button(
                                    onClick = { runner?.stopTask() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Stop")
                                }
                            }
                        }
                        if (agentSteps.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { showStepsSheet = true }) {
                                Text("View ${agentSteps.size} Agent Steps & Thoughts")
                            }
                        }
                    }
                }
            }

            // URL Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                browserController.navigate(urlInput)
                            }
                        }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Go")
                        }
                    }
                )
            }

            if (isLoading || isAgentRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Embedded Browser View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
            }

            // Quick Manual Controls
            Surface(
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                extractedContent = browserController.extractPageContent()
                                showInspectorSheet = true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Inspect DOM (${extractedContent?.elements?.size ?: 0})")
                    }

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                browserController.captureScreenshot()
                            }
                        }
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Capture")
                    }
                }
            }
        }
    }

    if (showStepsSheet && agentSteps.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showStepsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text("Autonomous Agent Steps", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(agentSteps) { step ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Step ${step.iteration}", fontWeight = FontWeight.Bold)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            step.actionName.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (step.thought.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("Thought: ${step.thought}", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Result: ${step.result}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInspectorSheet && extractedContent != null) {
        ModalBottomSheet(
            onDismissRequest = { showInspectorSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    "Page Elements & Agent Targets",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${extractedContent?.elements?.size ?: 0} interactive elements discovered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(extractedContent?.elements ?: emptyList()) { elem ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                elem.tagName.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            elem.selector,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (elem.text.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(elem.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            browserController.clickElement(elem.selector)
                                            showInspectorSheet = false
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
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
}
