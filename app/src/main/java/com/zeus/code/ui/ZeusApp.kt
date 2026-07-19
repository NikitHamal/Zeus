@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeus.code.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeus.code.BuildConfig
import com.zeus.code.model.AgentSession
import com.zeus.code.model.FileEntry
import com.zeus.code.model.MainTab
import com.zeus.code.model.PullRequest
import com.zeus.code.model.Repository
import com.zeus.code.model.Workspace
import com.zeus.code.ui.agent.AgentUiState
import com.zeus.code.ui.agent.BackgroundAgentScreen
import com.zeus.code.ui.agent.BackgroundAgentViewModel
import com.zeus.code.ui.theme.ZeusGold
import com.zeus.code.ui.theme.ZeusTheme
import com.zeus.code.ui.theme.ZeusThemeMode
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun ZeusApp(viewModel: MainViewModel, agentViewModel: BackgroundAgentViewModel) {
    val state by viewModel.state.collectAsState()
    ZeusTheme(mode = state.themeMode) {
        when {
            state.booting -> SplashScreen()
            !state.authenticated && !state.offlineMode -> LoginScreen(state, viewModel)
            else -> MainShell(state, viewModel, agentViewModel)
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Splash                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ZeusMark(84)
            Spacer(Modifier.height(20.dp))
            Text("Preparing your workspace", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Login                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
private fun LoginScreen(state: ZeusState, viewModel: MainViewModel) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ZeusMark(92)
            Spacer(Modifier.height(20.dp))
            Text("Zeus", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Your mobile GitHub workbench",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(30.dp))

            if (BuildConfig.OAUTH_CLIENT_ID.isBlank()) {
                InfoCard(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "OAuth client ID missing",
                    body = "Build with the OAUTH_CLIENT_ID Gradle property or GitHub Actions secret."
                )
                Spacer(Modifier.height(18.dp))
            }

            state.login?.let { login ->
                ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Authorize on GitHub", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Open GitHub, then enter this one-time code.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.clickable { clipboard.setText(AnnotatedString(login.code)) }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(login.code, style = MaterialTheme.typography.headlineMedium, letterSpacing = 4.sp)
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Rounded.ContentCopy, "Copy code")
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = { uriHandler.openUri(login.verificationUri) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.OpenInBrowser, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open GitHub")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Zeus is waiting securely for authorization…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } ?: run {
                Button(
                    onClick = viewModel::startLogin,
                    enabled = !state.busy && BuildConfig.OAUTH_CLIENT_ID.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Icon(Icons.Rounded.Source, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Continue with GitHub")
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = viewModel::continueOffline, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.FolderOpen, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Use local workspaces offline")
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "OAuth uses GitHub Device Flow. Your token is encrypted with Android Keystore and the client secret is never shipped in the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.busy && state.login == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Main shell + navigation                                                    */
/* ------------------------------------------------------------------------- */

private sealed interface WorkspaceRoute {
    data object List : WorkspaceRoute
    data object Detail : WorkspaceRoute
    data object Terminal : WorkspaceRoute
    data class Editor(val entry: FileEntry) : WorkspaceRoute
}

@Composable
private fun MainShell(state: ZeusState, viewModel: MainViewModel, agentViewModel: BackgroundAgentViewModel) {
    val agentState by agentViewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    val tab = MainTab.valueOf(selectedTab)
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var workspaceRoute by remember { mutableStateOf<WorkspaceRoute>(WorkspaceRoute.List) }

    // Derive the workspaces sub-route from the selected workspace.
    val baseWorkspaceRoute: WorkspaceRoute =
        if (state.selectedWorkspace == null) WorkspaceRoute.List else WorkspaceRoute.Detail
    val route: WorkspaceRoute = when (workspaceRoute) {
        is WorkspaceRoute.List, is WorkspaceRoute.Detail -> baseWorkspaceRoute
        is WorkspaceRoute.Terminal -> if (state.selectedWorkspace == null) WorkspaceRoute.List else WorkspaceRoute.Terminal
        is WorkspaceRoute.Editor ->
            if (state.selectedWorkspace == null) WorkspaceRoute.List else workspaceRoute
    }
    fun navWorkspace(next: WorkspaceRoute) { workspaceRoute = next }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(agentState.message) {
        agentState.message?.let {
            snackbar.showSnackbar(it)
            agentViewModel.dismissMessage()
        }
    }

    // Hierarchical back handling for every sub-route.
    BackHandler(enabled = route is WorkspaceRoute.Editor) { navWorkspace(WorkspaceRoute.Detail) }
    BackHandler(enabled = route is WorkspaceRoute.Terminal) { navWorkspace(WorkspaceRoute.Detail) }
    BackHandler(enabled = route == WorkspaceRoute.Detail) { viewModel.closeWorkspace() }
    BackHandler(enabled = tab == MainTab.GITHUB && state.selectedRepo != null) { viewModel.closeRepository() }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }

    val refreshAction: (() -> Unit)? = when {
        settingsOpen -> null
        tab == MainTab.AGENT -> ({ agentViewModel.refresh() })
        tab == MainTab.GITHUB && state.selectedRepo != null -> viewModel::refreshSelectedRepository
        tab == MainTab.GITHUB -> viewModel::refreshAccount
        tab == MainTab.WORKSPACES && route is WorkspaceRoute.Detail -> viewModel::refreshWorkspace
        tab == MainTab.WORKSPACES && route is WorkspaceRoute.List -> viewModel::refreshWorkspacesList
        else -> null
    }

    val title = when {
        settingsOpen -> "Settings"
        tab == MainTab.GITHUB && state.selectedRepo != null -> state.selectedRepo!!.name
        tab == MainTab.WORKSPACES && route is WorkspaceRoute.Terminal -> "Terminal"
        tab == MainTab.WORKSPACES && route is WorkspaceRoute.Detail -> state.selectedWorkspace!!.name
        else -> tabTitle(tab)
    }

    Scaffold(
        topBar = {
            // Settings is a standalone full-screen surface with its own header.
            if (route !is WorkspaceRoute.Editor && !settingsOpen) {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            when {
                                settingsOpen -> IconButton(onClick = { settingsOpen = false }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                                }
                                tab == MainTab.GITHUB && state.selectedRepo != null ->
                                    IconButton(onClick = viewModel::closeRepository) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to repositories")
                                    }
                                tab == MainTab.WORKSPACES && route is WorkspaceRoute.Terminal ->
                                    IconButton(onClick = { navWorkspace(WorkspaceRoute.Detail) }) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to workspace")
                                    }
                                tab == MainTab.WORKSPACES && route is WorkspaceRoute.Detail ->
                                    IconButton(onClick = viewModel::closeWorkspace) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to workspaces")
                                    }
                                else -> ZeusMark(34)
                            }
                        },
                        actions = {
                            if (refreshAction != null) {
                                IconButton(onClick = refreshAction) { Icon(Icons.Rounded.Refresh, "Refresh") }
                            }
                            if (!settingsOpen) {
                                IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, "Settings") }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                    if (state.busy || agentState.busy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        bottomBar = {
            if (route !is WorkspaceRoute.Editor && !settingsOpen) {
                NavigationBar {
                    MainTab.entries.forEach { item ->
                        // Badge semantics: only the Agent tab notifies, and only for
                        // sessions with activity the user has not viewed yet.
                        val count = when (item) {
                            MainTab.AGENT -> agentState.unreadIds.size
                            else -> 0
                        }
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                selectedTab = item.name
                                settingsOpen = false
                            },
                            icon = {
                                BadgedBox(badge = {
                                    if (count > 0) Badge { Text(count.coerceAtMost(99).toString()) }
                                }) { Icon(tabIcon(item), tabTitle(item)) }
                            },
                            label = { Text(tabTitle(item), maxLines = 1) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                settingsOpen -> SettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    agentState = agentState,
                    agentViewModel = agentViewModel,
                    onBack = { settingsOpen = false },
                    onOpenAgent = {
                        settingsOpen = false
                        selectedTab = MainTab.AGENT.name
                    }
                )
                else -> when (tab) {
                    MainTab.HOME -> HomeScreen(
                        state = state,
                        agentState = agentState,
                        onOpenWorkspace = { workspace ->
                            viewModel.selectWorkspace(workspace)
                            selectedTab = MainTab.WORKSPACES.name
                        },
                        onOpenSession = { session ->
                            agentViewModel.openSession(session)
                            selectedTab = MainTab.AGENT.name
                        },
                        onOpenTerminal = { workspace ->
                            viewModel.selectWorkspace(workspace)
                            selectedTab = MainTab.WORKSPACES.name
                            navWorkspace(WorkspaceRoute.Terminal)
                        }
                    )
                    MainTab.AGENT -> BackgroundAgentScreen(
                        viewModel = agentViewModel,
                        workspaces = state.workspaces,
                        onOpenWorkspace = { workspace ->
                            viewModel.selectWorkspace(workspace)
                            selectedTab = MainTab.WORKSPACES.name
                        },
                        onCloneBranch = { url, name, branch ->
                            viewModel.cloneUrl(url, name, branch)
                            selectedTab = MainTab.WORKSPACES.name
                        }
                    )
                    MainTab.WORKSPACES -> when (route) {
                        is WorkspaceRoute.List -> WorkspaceListScreen(
                            state = state,
                            viewModel = viewModel,
                            onOpen = { viewModel.selectWorkspace(it) }
                        )
                        is WorkspaceRoute.Detail -> WorkspaceDetailScreen(
                            state = state,
                            viewModel = viewModel,
                            onTerminal = { navWorkspace(WorkspaceRoute.Terminal) },
                            onEdit = { navWorkspace(WorkspaceRoute.Editor(it)) }
                        )
                        is WorkspaceRoute.Terminal -> TerminalScreen(state, viewModel)
                        is WorkspaceRoute.Editor -> FileEditorScreen(
                            entry = route.entry,
                            viewModel = viewModel,
                            onClose = { navWorkspace(WorkspaceRoute.Detail) }
                        )
                    }
                    MainTab.GITHUB -> if (state.selectedRepo == null) {
                        GitHubListScreen(state, viewModel)
                    } else {
                        RepositoryDetailScreen(state, viewModel, state.selectedRepo!!)
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Home                                                                       */
/* ------------------------------------------------------------------------- */

@Composable
private fun HomeScreen(
    state: ZeusState,
    agentState: AgentUiState,
    onOpenWorkspace: (Workspace) -> Unit,
    onOpenSession: (AgentSession) -> Unit,
    onOpenTerminal: (Workspace) -> Unit
) {
    val activeTasks = agentState.sessions.count { it.status in listOf("queued", "preparing", "running") }
    val recentTasks = if (agentState.authorized) agentState.sessions.take(3) else emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    greetingForNow(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    state.user?.name ?: state.user?.login ?: "coder",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeStat("Repositories", state.repositories.size, Icons.Rounded.Source, Modifier.weight(1f))
                HomeStat("Workspaces", state.workspaces.size, Icons.Rounded.Folder, Modifier.weight(1f))
                HomeStat("Active tasks", activeTasks, Icons.Rounded.AutoAwesome, Modifier.weight(1f))
            }
        }
        state.selectedWorkspace?.let { workspace ->
            item {
                SectionTitle("Active workspace")
                TonalCard(onClick = { onOpenWorkspace(workspace) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(if (workspace.gitRepository) Icons.Rounded.Source else Icons.Rounded.Folder)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workspace.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                workspace.currentBranch?.let { "Branch: $it" } ?: "Local folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onOpenTerminal(workspace) }) {
                            Icon(Icons.Rounded.Terminal, "Open terminal", Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (recentTasks.isNotEmpty()) {
            item { SectionTitle("Recent tasks") }
            items(recentTasks, key = { "home-${it.id}" }) { session ->
                TaskPeekRow(
                    session = session,
                    unread = session.id in agentState.unreadIds,
                    onClick = { onOpenSession(session) }
                )
            }
        }
    }
}

@Composable
private fun HomeStat(label: String, value: Int, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(value.toString(), style = MaterialTheme.typography.titleLarge)
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TaskPeekRow(session: AgentSession, unread: Boolean, onClick: () -> Unit) {
    TonalCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(session.status)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.title,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (unread) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Text(
                    "${session.repoFullName} · ${session.progress}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (session.updatedAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.updatedAt)) else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusDot(status: String) {
    val color = when (status) {
        "completed" -> MaterialTheme.colorScheme.tertiary
        "failed", "cancelled" -> MaterialTheme.colorScheme.error
        "queued", "preparing", "running" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

private fun greetingForNow(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..20 -> "Good evening"
    else -> "Good night"
}

/* ------------------------------------------------------------------------- */
/* Workspaces                                                                 */
/* ------------------------------------------------------------------------- */

@Composable
private fun WorkspaceListScreen(state: ZeusState, viewModel: MainViewModel, onOpen: (Workspace) -> Unit) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importWorkspace)
    }
    var createDialog by remember { mutableStateOf(false) }
    var cloneDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Workspace?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { createDialog = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("New") }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TonalChip(onClick = { importLauncher.launch(null) }, icon = Icons.Rounded.Upload, label = "Import folder")
                    TonalChip(onClick = { cloneDialog = true }, icon = Icons.Rounded.CloudDownload, label = "Clone URL")
                }
            }
            if (state.workspaces.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.FolderOpen,
                        title = "No workspaces yet",
                        body = "Import a folder, clone a repository, or create a new project."
                    )
                }
            } else {
                items(state.workspaces, key = { it.path }) { workspace ->
                    TonalCard(onClick = { onOpen(workspace) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(if (workspace.gitRepository) Icons.Rounded.Source else Icons.Rounded.Folder)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(workspace.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    workspace.currentBranch?.let { "Branch: $it" } ?: "Local folder",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                workspace.remoteUrl?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { deleteTarget = workspace }) {
                                Icon(Icons.Rounded.Delete, "Delete workspace", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (createDialog) NewWorkspaceDialog(onDismiss = { createDialog = false }) { name, git ->
        createDialog = false
        viewModel.createWorkspace(name, git)
    }
    if (cloneDialog) CloneDialog(onDismiss = { cloneDialog = false }) { url, name ->
        cloneDialog = false
        viewModel.cloneUrl(url, name)
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete ${target.name}?",
            body = "This removes the local workspace from this device. Any linked GitHub repository is not affected.",
            destructive = true,
            onDismiss = { deleteTarget = null }
        ) {
            deleteTarget = null
            viewModel.deleteWorkspace(target)
        }
    }
}

@Composable
private fun WorkspaceDetailScreen(
    state: ZeusState,
    viewModel: MainViewModel,
    onTerminal: () -> Unit,
    onEdit: (FileEntry) -> Unit
) {
    val workspace = state.selectedWorkspace ?: return
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::exportWorkspace)
    }
    var showCommit by remember { mutableStateOf(false) }
    var showBranch by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showNewPath by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var section by rememberSaveable(workspace.path) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Header block
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                workspace.path,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TonalChip(
                    onClick = { showCommit = true },
                    icon = Icons.Rounded.Check,
                    label = "Commit",
                    enabled = workspace.gitRepository
                )
                TonalChip(
                    onClick = { viewModel.push() },
                    icon = Icons.Rounded.ArrowUpward,
                    label = "Push",
                    enabled = workspace.gitRepository
                )
                TonalChip(
                    onClick = { viewModel.pull() },
                    icon = Icons.Rounded.ArrowDownward,
                    label = "Pull",
                    enabled = workspace.gitRepository
                )
                TonalChip(onClick = onTerminal, icon = Icons.Rounded.Terminal, label = "Terminal")
                TonalChip(onClick = { showNewPath = true }, icon = Icons.Rounded.CreateNewFolder, label = "New file")
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More actions") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!workspace.gitRepository) {
                            DropdownMenuItem(
                                text = { Text("Initialize Git") },
                                leadingIcon = { Icon(Icons.Rounded.Source, null) },
                                onClick = { menu = false; viewModel.initializeGit() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Branches") },
                            enabled = workspace.gitRepository,
                            leadingIcon = { Icon(Icons.Rounded.AccountTree, null) },
                            onClick = { menu = false; showBranch = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Advanced Git") },
                            enabled = workspace.gitRepository,
                            leadingIcon = { Icon(Icons.Rounded.Bolt, null) },
                            onClick = { menu = false; showAdvanced = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Export to folder") },
                            leadingIcon = { Icon(Icons.Rounded.Upload, null) },
                            onClick = { menu = false; exportLauncher.launch(null) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = section == 0,
                    onClick = { section = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Files") }
                SegmentedButton(
                    selected = section == 1,
                    onClick = { section = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Git history") }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (section == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (state.files.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Description,
                            title = "Empty workspace",
                            body = "Create a file or import project content."
                        )
                    }
                } else {
                    items(state.files, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            onOpen = { if (!entry.directory) onEdit(entry) },
                            onDelete = { viewModel.deletePath(entry) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        SelectionContainer {
                            Text(
                                state.gitStatus,
                                Modifier.padding(16.dp),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                item { SectionTitle("Recent commits") }
                if (state.commits.isEmpty()) {
                    item {
                        EmptyState(Icons.Rounded.Source, "No commits", "Initialize Git and create your first commit.")
                    }
                } else {
                    items(state.commits, key = { it.hash }) { commit ->
                        TonalCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            commit.shortHash,
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(commit.message, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${commit.author} · ${DateFormat.getDateTimeInstance().format(Date(commit.timestamp))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCommit) CommitDialog(onDismiss = { showCommit = false }) {
        showCommit = false
        viewModel.commit(it)
    }
    if (showBranch) BranchSheet(
        viewModel = viewModel,
        onDismiss = { showBranch = false },
        onCheckout = { name, create ->
            showBranch = false
            viewModel.checkout(name, create)
        }
    )
    if (showAdvanced) AdvancedGitSheet(state, onDismiss = { showAdvanced = false }, viewModel = viewModel)
    if (showNewPath) NewPathDialog(onDismiss = { showNewPath = false }) { path, directory ->
        showNewPath = false
        viewModel.createPath(path, directory)
    }
}

/* ------------------------------------------------------------------------- */
/* Terminal (workspace sub-route)                                             */
/* ------------------------------------------------------------------------- */

@Composable
private fun TerminalScreen(state: ZeusState, viewModel: MainViewModel) {
    var command by rememberSaveable { mutableStateOf("") }
    val workspace = state.selectedWorkspace ?: return
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                Text(
                    workspace.name,
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::clearTerminal) { Icon(Icons.Rounded.Clear, "Clear terminal") }
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            color = Color(0xFF111014),
            shape = MaterialTheme.shapes.large
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.terminalLines) { line ->
                    SelectionContainer {
                        Text(
                            line,
                            color = Color(0xFFEDEAF2),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${'$'}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp, end = 10.dp)
            )
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("git status") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val value = command.trim()
                    if (value.isNotBlank()) {
                        viewModel.runTerminal(value)
                        command = ""
                    }
                },
                enabled = command.isNotBlank() && !state.busy
            ) { Icon(Icons.Rounded.KeyboardArrowRight, "Run") }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* File editor (full screen)                                                  */
/* ------------------------------------------------------------------------- */

@Composable
private fun FileEditorScreen(entry: FileEntry, viewModel: MainViewModel, onClose: () -> Unit) {
    var text by remember(entry.path) { mutableStateOf("") }
    var loading by remember(entry.path) { mutableStateOf(true) }
    var error by remember(entry.path) { mutableStateOf<String?>(null) }
    var dirty by remember(entry.path) { mutableStateOf(false) }
    LaunchedEffect(entry.path) {
        runCatching { viewModel.readFile(entry) }
            .onSuccess { text = it }
            .onFailure { error = it.message }
        loading = false
    }
    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close editor") }
            },
            actions = {
                TextButton(
                    onClick = { viewModel.saveFile(entry, text); onClose() },
                    enabled = !loading && error == null && dirty
                ) {
                    Icon(Icons.Rounded.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Column(
                Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            else -> OutlinedTextField(
                value = text,
                onValueChange = { text = it; dirty = true },
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = MaterialTheme.shapes.large
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* GitHub                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun GitHubListScreen(state: ZeusState, viewModel: MainViewModel) {
    var createDialog by remember { mutableStateOf(false) }

    if (!state.authenticated) {
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Rounded.Source,
                title = "GitHub is offline",
                body = "Connect GitHub from Settings to manage repositories."
            )
        }
        return
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { createDialog = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Repository") }
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = state.repoQuery,
                onValueChange = viewModel::setRepoQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search repositories") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (state.repoQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setRepoQuery("") }) { Icon(Icons.Rounded.Clear, "Clear search") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.filteredRepositories, key = { it.id }) { repo ->
                    RepositoryCard(repo, onOpen = { viewModel.selectRepository(repo) }, onClone = { viewModel.cloneRepository(repo) })
                }
            }
        }
    }
    if (createDialog) NewRepositoryDialog(onDismiss = { createDialog = false }) { name, description, private, init ->
        createDialog = false
        viewModel.createRepository(name, description, private, init)
    }
}

@Composable
private fun RepositoryDetailScreen(state: ZeusState, viewModel: MainViewModel, repo: Repository) {
    var tab by rememberSaveable(repo.id.toString()) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var issueDialog by remember { mutableStateOf(false) }
    var pullDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var reviewPull by remember { mutableStateOf<PullRequest?>(null) }
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            Text(repo.owner.login, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                repo.description ?: "No description",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetaChip(Icons.Rounded.AccountTree, repo.defaultBranch)
                MetaChip(Icons.Rounded.Star, repo.stars.toString())
                repo.language?.let { MetaChip(Icons.Rounded.Bolt, it) }
                if (repo.private) MetaChip(Icons.Rounded.Lock, "Private")
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { viewModel.cloneRepository(repo) }) {
                    Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clone")
                }
                FilledTonalButton(onClick = { viewModel.forkRepository(repo) }) {
                    Icon(Icons.Rounded.ForkRight, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fork")
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Repository options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Open on GitHub") },
                            leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                            onClick = { menu = false; uriHandler.openUri(repo.htmlUrl) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete repository") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; deleteConfirm = true }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Overview") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Pulls (${state.pullRequests.size})") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Issues (${state.issues.size})") })
            }
        }

        when (tab) {
            0 -> LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { SectionTitle("Branches") }
                if (state.branches.isEmpty()) {
                    item { Text("No branches loaded.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.branches, key = { it.name }) { branch ->
                    TonalCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AccountTree, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(branch.name, Modifier.weight(1f), fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (branch.protected) {
                                Text("Protected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    FilledTonalButton(onClick = { pullDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("New pull request")
                    }
                }
                if (state.pullRequests.isEmpty()) {
                    item { EmptyState(Icons.Rounded.CallMerge, "No pull requests", "Create one from a pushed branch.") }
                }
                items(state.pullRequests, key = { it.number }) { pull ->
                    PullRow(pull, onMerge = { viewModel.mergePull(pull, "merge") }, onReview = { reviewPull = pull })
                }
            }
            2 -> LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    FilledTonalButton(onClick = { issueDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("New issue")
                    }
                }
                if (state.issues.isEmpty()) {
                    item { EmptyState(Icons.Rounded.ErrorOutline, "No issues", "This repository has no issues.") }
                }
                items(state.issues, key = { it.number }) { issue ->
                    TonalCard {
                        Column {
                            Text("#${issue.number} · ${issue.title}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${issue.state} · ${issue.user.login}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleteConfirm) ConfirmDialog(
        title = "Delete ${repo.fullName}?",
        body = "This permanently deletes the GitHub repository. This cannot be undone.",
        destructive = true,
        onDismiss = { deleteConfirm = false }
    ) {
        deleteConfirm = false
        viewModel.deleteRepository(repo)
    }
    if (issueDialog) TextBodyDialog("Create issue", "Issue title", onDismiss = { issueDialog = false }) { title, body ->
        issueDialog = false
        viewModel.createIssue(title, body)
    }
    if (pullDialog) PullRequestDialog(repo.defaultBranch, onDismiss = { pullDialog = false }) { title, head, base, body ->
        pullDialog = false
        viewModel.createPull(title, head, base, body)
    }
    reviewPull?.let { pull ->
        ReviewDialog(pull, onDismiss = { reviewPull = null }) { body, event ->
            reviewPull = null
            viewModel.reviewPull(pull, body, event)
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Settings                                                                   */
/* ------------------------------------------------------------------------- */

@Composable
private fun SettingsScreen(
    state: ZeusState,
    viewModel: MainViewModel,
    agentState: AgentUiState,
    agentViewModel: BackgroundAgentViewModel,
    onBack: () -> Unit,
    onOpenAgent: () -> Unit
) {
    val context = LocalContext.current
    var disconnectConfirm by remember { mutableStateOf(false) }
    var agentDisconnectConfirm by remember { mutableStateOf(false) }
    val modes = listOf(ZeusThemeMode.LIGHT, ZeusThemeMode.DARK, ZeusThemeMode.SYSTEM)

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("Settings", style = MaterialTheme.typography.titleLarge, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SectionTitle("Appearance") }
            item {
                TonalCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Palette)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Theme", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Pick a look for Zeus.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                            ) { Text(mode.label, maxLines = 1) }
                        }
                    }
                }
            }

            item { SectionTitle("GitHub account") }
            item {
                TonalCard {
                    Row(
                        modifier = Modifier.height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            Modifier.size(44.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    (state.user?.login ?: "Z").take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                state.user?.name ?: state.user?.login ?: "Offline user",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(7.dp).clip(CircleShape).background(
                                        if (state.authenticated) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.outline
                                    )
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.authenticated) "Connected to GitHub" else "Local mode — GitHub is offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/connections/applications"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Manage GitHub authorization", maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { if (state.authenticated) disconnectConfirm = true else viewModel.logout() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.authenticated) "Disconnect GitHub" else "Exit offline mode", maxLines = 1)
                    }
                }
            }

            item { SectionTitle("Background agent") }
            item {
                TonalCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.AutoAwesome)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("NEBians", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (agentState.authorized) {
                                    agentState.me?.user?.let { user ->
                                        "Connected · ${user.displayName.ifBlank { user.username }.ifBlank { "device" }}"
                                    } ?: "Connected"
                                } else "Not connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (agentState.authorized) {
                            IconButton(onClick = { agentDisconnectConfirm = true }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.LinkOff,
                                    "Disconnect NEBians",
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    if (!agentState.authorized) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onOpenAgent, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect from the Agent tab", maxLines = 1)
                        }
                    }
                }
            }

            item { SectionTitle("About") }
            item { AppUpdateCard() }
            item {
                SettingRow(Icons.Rounded.Bolt, "Zeus version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
            item {
                SettingRow(Icons.Rounded.Folder, "Workspace storage", "Private app storage; exported copies omit .git")
            }
            item { Spacer(Modifier.height(22.dp)) }
        }
    }
    if (disconnectConfirm) ConfirmDialog(
        title = "Disconnect GitHub?",
        body = "Zeus will remove the encrypted GitHub token from this device. Local workspaces stay available.",
        destructive = false,
        onDismiss = { disconnectConfirm = false }
    ) {
        disconnectConfirm = false
        viewModel.logout()
    }
    if (agentDisconnectConfirm) ConfirmDialog(
        title = "Disconnect NEBians?",
        body = "This revokes the saved background-agent device token. Your tasks remain on NEBians.",
        destructive = true,
        onDismiss = { agentDisconnectConfirm = false }
    ) {
        agentDisconnectConfirm = false
        agentViewModel.disconnect()
    }
}

/* ------------------------------------------------------------------------- */
/* Shared building blocks                                                     */
/* ------------------------------------------------------------------------- */

@Composable
private fun ZeusMark(size: Int) {
    Surface(
        Modifier.padding(8.dp).size(size.dp),
        shape = RoundedCornerShape((size / 3.2f).dp),
        color = ZeusGold
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Bolt,
                "Zeus",
                tint = Color(0xFF2A2200),
                modifier = Modifier.size((size * .6f).dp)
            )
        }
    }
}

@Composable
private fun TonalCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val shape = MaterialTheme.shapes.large
    val elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        ElevatedCard(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            elevation = elevation
        ) { Box(Modifier.padding(16.dp)) { content() } }
    } else {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            elevation = elevation
        ) { Box(Modifier.padding(16.dp)) { content() } }
    }
}

@Composable
private fun TonalChip(onClick: () -> Unit, icon: ImageVector, label: String, enabled: Boolean = true) {
    FilledTonalButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Icon(icon, null, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun MetaChip(icon: ImageVector, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Surface(
        Modifier.size(44.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
    )
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(Modifier.size(68.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String) {
    TonalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = !entry.directory, onClick = onOpen)
            .padding(start = (10 + entry.depth * 14).dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (entry.directory) Icons.Rounded.Folder else Icons.Rounded.Description,
            null,
            Modifier.size(20.dp),
            tint = if (entry.directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(entry.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!entry.directory) {
            Text(
                formatSize(entry.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RepositoryCard(repo: Repository, onOpen: () -> Unit, onClone: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    TonalCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (repo.private) Icons.Rounded.Lock else Icons.Rounded.Source)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(repo.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    repo.description ?: "No description",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repo.language?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    Text("★ ${repo.stars}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    Text("Issues ${repo.openIssues}", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Repository options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Clone") }, onClick = { menu = false; onClone() }, leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) })
                    DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                }
            }
        }
    }
}

@Composable
private fun PullRow(pull: PullRequest, onMerge: () -> Unit, onReview: () -> Unit) {
    TonalCard {
        Column {
            Text("#${pull.number} · ${pull.title}", style = MaterialTheme.typography.titleSmall)
            Text(
                "${pull.head.ref} → ${pull.base.ref} · ${pull.user.login}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onReview) { Text("Review") }
                Button(onClick = onMerge, enabled = pull.state == "open" && !pull.draft) {
                    Icon(Icons.Rounded.CallMerge, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Merge")
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Dialogs & sheets                                                           */
/* ------------------------------------------------------------------------- */

@Composable
private fun NewWorkspaceDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var git by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New workspace") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(git, { git = it })
                    Text("Initialize Git repository")
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, git) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CloneDialog(onDismiss: () -> Unit, onClone: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(url, { url = it }, label = { Text("GitHub clone URL") }, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Workspace name (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { onClone(url, name.ifBlank { url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git") }) },
                enabled = url.isNotBlank()
            ) { Text("Clone") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NewRepositoryDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var private by remember { mutableStateOf(false) }
    var init by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Repository name") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Description") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(private, { private = it })
                    Spacer(Modifier.width(10.dp))
                    Text("Private repository")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(init, { init = it })
                    Text("Initialize with README")
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, description, private, init) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CommitDialog(onDismiss: () -> Unit, onCommit: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Commit all changes") },
        text = { OutlinedTextField(message, { message = it }, label = { Text("Commit message") }, minLines = 3) },
        confirmButton = { Button(onClick = { onCommit(message) }, enabled = message.isNotBlank()) { Text("Commit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BranchSheet(viewModel: MainViewModel, onDismiss: () -> Unit, onCheckout: (String, Boolean) -> Unit) {
    var branches by remember { mutableStateOf<List<String>?>(null) }
    var branch by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { branches = viewModel.workspaceBranches() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Switch branch", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            when {
                branches == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading branches…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                !branches.isNullOrEmpty() -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    branches!!.forEach { name ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                branch = name
                                create = false
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = branch == name && !create, onClick = { branch = name; create = false })
                            Icon(Icons.Rounded.AccountTree, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(name, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it; create = true },
                label = { Text("Or a new branch name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { onCheckout(branch.trim(), create) },
                enabled = branch.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (create) "Create and switch" else "Switch branch") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdvancedGitSheet(state: ZeusState, onDismiss: () -> Unit, viewModel: MainViewModel) {
    var resetTarget by remember { mutableStateOf("HEAD") }
    var mergeBranch by remember { mutableStateOf("") }
    var revertCommit by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf(state.selectedWorkspace?.remoteUrl.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Advanced Git", style = MaterialTheme.typography.titleLarge) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.fetch() }, modifier = Modifier.weight(1f)) { Text("Fetch", maxLines = 1) }
                    FilledTonalButton(onClick = { viewModel.stash() }, modifier = Modifier.weight(1f)) { Text("Stash", maxLines = 1) }
                    FilledTonalButton(onClick = { viewModel.stashApply() }, modifier = Modifier.weight(1f)) { Text("Apply", maxLines = 1) }
                }
            }
            item { Button(onClick = { viewModel.push(true); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Force push") } }
            item { HorizontalDivider() }
            item {
                SheetActionField(
                    value = mergeBranch,
                    onValueChange = { mergeBranch = it },
                    label = "Branch to merge",
                    actionLabel = "Merge branch",
                    enabled = mergeBranch.isNotBlank()
                ) { viewModel.merge(mergeBranch); onDismiss() }
            }
            item {
                SheetActionField(
                    value = resetTarget,
                    onValueChange = { resetTarget = it },
                    label = "Hard reset target",
                    actionLabel = "Reset --hard",
                    enabled = resetTarget.isNotBlank()
                ) { viewModel.hardReset(resetTarget); onDismiss() }
            }
            item {
                SheetActionField(
                    value = revertCommit,
                    onValueChange = { revertCommit = it },
                    label = "Commit to revert",
                    actionLabel = "Revert commit",
                    enabled = revertCommit.isNotBlank()
                ) { viewModel.revert(revertCommit); onDismiss() }
            }
            item {
                SheetActionField(
                    value = remote,
                    onValueChange = { remote = it },
                    label = "Origin remote URL",
                    actionLabel = "Update remote",
                    enabled = remote.isNotBlank()
                ) { viewModel.setRemote(remote); onDismiss() }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SheetActionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = onAction, enabled = enabled) { Text(actionLabel) }
}

@Composable
private fun NewPathDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var path by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create path") },
        text = {
            Column {
                OutlinedTextField(path, { path = it }, label = { Text("Relative path") }, placeholder = { Text("src/main.kt") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(directory, { directory = it })
                    Text("Create directory")
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(path, directory) }, enabled = path.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TextBodyDialog(titleText: String, titleLabel: String, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(titleLabel) })
                OutlinedTextField(body, { body = it }, label = { Text("Description") }, minLines = 5)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(title, body) }, enabled = title.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PullRequestDialog(defaultBase: String, onDismiss: () -> Unit, onSubmit: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    var base by remember { mutableStateOf(defaultBase) }
    var body by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New pull request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") })
                OutlinedTextField(head, { head = it }, label = { Text("Head branch") })
                OutlinedTextField(base, { base = it }, label = { Text("Base branch") })
                OutlinedTextField(body, { body = it }, label = { Text("Description") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(title, head, base, body) }, enabled = title.isNotBlank() && head.isNotBlank() && base.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReviewDialog(pull: PullRequest, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var body by remember { mutableStateOf("") }
    var event by remember { mutableStateOf("COMMENT") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review #${pull.number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(body, { body = it }, label = { Text("Review comments") }, minLines = 5)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("COMMENT", "APPROVE", "REQUEST_CHANGES").forEach { value ->
                        FilterChip(selected = event == value, onClick = { event = value }, label = { Text(value.replace('_', ' ')) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSubmit(body, event) }, enabled = body.isNotBlank() || event == "APPROVE") { Text("Submit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    destructive: Boolean,
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
            ) { Text(if (destructive) "Delete" else "Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ------------------------------------------------------------------------- */
/* Utilities                                                                  */
/* ------------------------------------------------------------------------- */

private fun tabTitle(tab: MainTab) = when (tab) {
    MainTab.HOME -> "Home"
    MainTab.AGENT -> "Agent"
    MainTab.WORKSPACES -> "Workspaces"
    MainTab.GITHUB -> "GitHub"
}

private fun tabIcon(tab: MainTab) = when (tab) {
    MainTab.HOME -> Icons.Rounded.Home
    MainTab.AGENT -> Icons.Rounded.AutoAwesome
    MainTab.WORKSPACES -> Icons.Rounded.Folder
    MainTab.GITHUB -> Icons.Rounded.Source
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
