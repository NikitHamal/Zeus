package com.zeus.code.ui.automation

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.code.automation.PhoneController
import com.zeus.code.automation.UiElementNode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneControllerScreen(
    phoneController: PhoneController,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isServiceActive by phoneController.isServiceActive.collectAsState()
    val logs by phoneController.logs.collectAsState()
    var inspectedNodes by remember { mutableStateOf<List<UiElementNode>>(emptyList()) }
    var targetPackage by remember { mutableStateOf("com.android.settings") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        phoneController.checkServiceState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Phone Controller", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = if (isServiceActive) "Accessibility Automation Connected" else "Service Inactive",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
                    IconButton(onClick = { phoneController.checkServiceState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Permission / Service Status Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isServiceActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (isServiceActive) "System Automation Active" else "Enable Accessibility Service",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isServiceActive)
                                "Zeus can inspect screen nodes, tap coordinates, dispatch swipes, and navigate across all Android apps."
                            else
                                "To control real phone apps and screens, enable Zeus Phone Automation in your Android Accessibility Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isServiceActive) {
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { phoneController.openAccessibilitySettings() }) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Accessibility Settings")
                            }
                        }
                    }
                }
            }

            // Quick Device Actions
            item {
                Text("Quick Device Actions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { scope.launch { phoneController.pressHome() } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Home")
                    }
                    FilledTonalButton(
                        onClick = { scope.launch { phoneController.pressBack() } },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
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
                        Icon(Icons.Default.ScreenSearchDesktop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Inspect")
                    }
                }
            }

            // Launch App Section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Launch Android App", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = targetPackage,
                                onValueChange = { targetPackage = it },
                                placeholder = { Text("e.g. com.android.chrome") },
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

            // Inspected Nodes (if any)
            if (inspectedNodes.isNotEmpty()) {
                item {
                    Text("Detected Screen Nodes (${inspectedNodes.size})", fontWeight = FontWeight.Bold)
                }
                items(inspectedNodes.take(20)) { node ->
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

            // Action Log History
            item {
                Text("Automation Activity Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
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
