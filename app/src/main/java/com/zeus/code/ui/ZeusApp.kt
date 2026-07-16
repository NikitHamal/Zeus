@file:OptIn(ExperimentalMaterial3Api::class)

package com.zeus.code.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.AccountTree
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
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.zeus.code.model.FileEntry
import com.zeus.code.model.MainTab
import com.zeus.code.model.PullRequest
import com.zeus.code.model.Repository
import com.zeus.code.model.Workspace
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ZeusApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    when {
        state.booting -> SplashScreen()
        !state.authenticated && !state.offlineMode -> LoginScreen(state, viewModel)
        else -> MainShell(state, viewModel)
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ZeusMark(80)
            Spacer(Modifier.height(20.dp))
            Text("Preparing your workspace", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

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
            Spacer(Modifier.height(24.dp))
            Text("Zeus", style = MaterialTheme.typography.displaySmall)
            Text(
                "Your mobile GitHub workbench",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            if (BuildConfig.OAUTH_CLIENT_ID.isBlank()) {
                InfoCard(
                    icon = Icons.Rounded.ErrorOutline,
                    title = "OAuth client ID missing",
                    body = "Build with the OAUTH_CLIENT_ID Gradle property or GitHub Actions secret."
                )
                Spacer(Modifier.height(18.dp))
            }

            state.login?.let { login ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Authorize on GitHub", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Open GitHub, then enter this one-time code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { clipboard.setText(AnnotatedString(login.code)) }
                        ) {
                            Row(Modifier.padding(horizontal = 22.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(login.code, style = MaterialTheme.typography.headlineMedium, letterSpacing = 4.sp)
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Rounded.ContentCopy, null)
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
                Spacer(Modifier.height(24.dp))
                Text(
                    "OAuth uses GitHub Device Flow. Your token is encrypted with Android Keystore and the client secret is never shipped in the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.busy && state.login == null) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
    }
}

@Composable
private fun MainShell(state: ZeusState, viewModel: MainViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(tabTitle(selectedTab), style = MaterialTheme.typography.titleLarge)
                            state.selectedWorkspace?.let {
                                Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = { ZeusMark(36) },
                    actions = {
                        IconButton(onClick = {
                            when (selectedTab) {
                                MainTab.GITHUB -> viewModel.refreshAccount()
                                MainTab.WORKSPACES, MainTab.TERMINAL -> viewModel.refreshWorkspace()
                                else -> Unit
                            }
                        }) { Icon(Icons.Rounded.Refresh, "Refresh") }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
                AnimatedVisibility(state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val count = when (tab) {
                        MainTab.GITHUB -> state.repositories.size
                        MainTab.WORKSPACES -> state.workspaces.size
                        else -> 0
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            BadgedBox(badge = {
                                if (count > 0 && tab in listOf(MainTab.GITHUB, MainTab.WORKSPACES)) Badge { Text(count.coerceAtMost(99).toString()) }
                            }) { Icon(tabIcon(tab), tabTitle(tab)) }
                        },
                        label = { Text(tabTitle(tab)) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        AnimatedContent(selectedTab, label = "main-tab") { tab ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    MainTab.HOME -> HomeScreen(state) { selectedTab = it }
                    MainTab.WORKSPACES -> WorkspacesScreen(state, viewModel)
                    MainTab.GITHUB -> GitHubScreen(state, viewModel)
                    MainTab.TERMINAL -> TerminalScreen(state, viewModel)
                    MainTab.SETTINGS -> SettingsScreen(state, viewModel)
                }
            }
        }
    }

    state.selectedRepo?.let { RepositoryDialog(state, viewModel, it) }
}

@Composable
private fun HomeScreen(state: ZeusState, navigate: (MainTab) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Build anywhere.",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                state.user?.let { "Welcome back, ${it.name ?: it.login}." } ?: "Your local coding cockpit is ready.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Repositories", state.repositories.size.toString(), Icons.Rounded.Source, Modifier.weight(1f))
                MetricCard("Workspaces", state.workspaces.size.toString(), Icons.Rounded.Folder, Modifier.weight(1f))
            }
        }
        item {
            Text("Quick actions", style = MaterialTheme.typography.titleLarge)
        }
        item {
            QuickAction(Icons.Rounded.CloudDownload, "Clone a repository", "Bring a GitHub project onto your phone") { navigate(MainTab.GITHUB) }
            QuickAction(Icons.Rounded.Edit, "Edit local files", "Open an imported workspace and save changes") { navigate(MainTab.WORKSPACES) }
            QuickAction(Icons.Rounded.Terminal, "Run commands", "Use Git shortcuts and Android shell commands") { navigate(MainTab.TERMINAL) }
        }
        state.selectedWorkspace?.let { workspace ->
            item {
                Text("Active workspace", style = MaterialTheme.typography.titleLarge)
                WorkspaceCard(workspace, selected = true, onOpen = { navigate(MainTab.WORKSPACES) }, onDelete = null)
            }
        }
        item {
            InfoCard(
                icon = Icons.Rounded.Info,
                title = "Android terminal limits",
                body = "Regular commands run inside Zeus using /system/bin/sh. Git commands are implemented with JGit, so commit, push, pull, merge, reset and branches work even when the device has no git binary."
            )
        }
    }
}

@Composable
private fun WorkspacesScreen(state: ZeusState, viewModel: MainViewModel) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importWorkspace)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::exportWorkspace)
    }
    var createDialog by remember { mutableStateOf(false) }
    var cloneDialog by remember { mutableStateOf(false) }

    if (state.selectedWorkspace == null) {
        Scaffold(
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = { createDialog = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New") })
            }
        ) { inner ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AssistChip(onClick = { importLauncher.launch(null) }, label = { Text("Import folder") }, leadingIcon = { Icon(Icons.Rounded.Upload, null) })
                        AssistChip(onClick = { cloneDialog = true }, label = { Text("Clone URL") }, leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) })
                    }
                }
                if (state.workspaces.isEmpty()) {
                    item { EmptyState(Icons.Rounded.FolderOpen, "No workspaces yet", "Import a folder, clone a repository, or create a new project.") }
                } else {
                    items(state.workspaces, key = { it.path }) { workspace ->
                        WorkspaceCard(
                            workspace = workspace,
                            selected = false,
                            onOpen = { viewModel.selectWorkspace(workspace) },
                            onDelete = { viewModel.deleteWorkspace(workspace) }
                        )
                    }
                }
            }
        }
    } else {
        WorkspaceDetail(state, viewModel, onExport = { exportLauncher.launch(null) }, onClose = viewModel::closeWorkspace)
    }

    if (createDialog) NewWorkspaceDialog(onDismiss = { createDialog = false }) { name, git ->
        createDialog = false
        viewModel.createWorkspace(name, git)
    }
    if (cloneDialog) CloneDialog(onDismiss = { cloneDialog = false }) { url, name ->
        cloneDialog = false
        viewModel.cloneUrl(url, name)
    }
}

@Composable
private fun WorkspaceDetail(state: ZeusState, viewModel: MainViewModel, onExport: () -> Unit, onClose: () -> Unit) {
    val workspace = state.selectedWorkspace ?: return
    var showCommit by remember { mutableStateOf(false) }
    var showBranch by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showNewPath by remember { mutableStateOf(false) }
    var editorEntry by remember { mutableStateOf<FileEntry?>(null) }
    var tab by rememberSaveable(workspace.path) { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(onClick = onClose, label = { Text("All") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) })
            AssistChip(onClick = { tab = 0 }, label = { Text("Files") }, leadingIcon = { Icon(Icons.Rounded.Folder, null) })
            AssistChip(onClick = { tab = 1 }, label = { Text("Git") }, leadingIcon = { Icon(Icons.Rounded.AccountTree, null) })
            if (!workspace.gitRepository) AssistChip(onClick = viewModel::initializeGit, label = { Text("Init Git") }, leadingIcon = { Icon(Icons.Rounded.Source, null) })
            AssistChip(onClick = { showCommit = true }, enabled = workspace.gitRepository, label = { Text("Commit") }, leadingIcon = { Icon(Icons.Rounded.Check, null) })
            AssistChip(onClick = { viewModel.push() }, enabled = workspace.gitRepository, label = { Text("Push") }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) })
            AssistChip(onClick = { viewModel.pull() }, enabled = workspace.gitRepository, label = { Text("Pull") }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) })
            AssistChip(onClick = onExport, label = { Text("Export") }, leadingIcon = { Icon(Icons.Rounded.Upload, null) })
            AssistChip(onClick = { showAdvanced = true }, label = { Text("More") }, leadingIcon = { Icon(Icons.Rounded.MoreVert, null) })
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Files") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }
        if (tab == 0) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(workspace.name, style = MaterialTheme.typography.titleLarge)
                                Text(workspace.path, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { showNewPath = true }) { Icon(Icons.Rounded.CreateNewFolder, "Create file or folder") }
                        }
                    }
                    if (state.files.isEmpty()) item { EmptyState(Icons.Rounded.Description, "Empty workspace", "Create a file or import project content.") }
                    items(state.files, key = { it.path }) { entry ->
                        FileRow(entry, onOpen = { if (!entry.directory) editorEntry = entry }, onDelete = { viewModel.deletePath(entry) })
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Text(state.gitStatus, Modifier.padding(16.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { Text("Recent commits", style = MaterialTheme.typography.titleLarge) }
                if (state.commits.isEmpty()) item { EmptyState(Icons.Rounded.Source, "No commits", "Initialize Git and create your first commit.") }
                items(state.commits, key = { it.hash }) { commit ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(commit.shortHash, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(commit.message, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2)
                            }
                            Text("${commit.author} · ${DateFormat.getDateTimeInstance().format(Date(commit.timestamp))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    if (showBranch) BranchDialog(onDismiss = { showBranch = false }, onCheckout = { name, create ->
        showBranch = false
        viewModel.checkout(name, create)
    })
    if (showAdvanced) AdvancedGitDialog(
        state = state,
        onDismiss = { showAdvanced = false },
        onBranch = { showAdvanced = false; showBranch = true },
        viewModel = viewModel
    )
    if (showNewPath) NewPathDialog(onDismiss = { showNewPath = false }) { path, directory ->
        showNewPath = false
        viewModel.createPath(path, directory)
    }
    editorEntry?.let { entry ->
        FileEditorDialog(entry, viewModel, onDismiss = { editorEntry = null })
    }
}

@Composable
private fun GitHubScreen(state: ZeusState, viewModel: MainViewModel) {
    var createDialog by remember { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            if (state.authenticated) ExtendedFloatingActionButton(onClick = { createDialog = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Repository") })
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (!state.authenticated) {
                EmptyState(Icons.Rounded.Source, "GitHub is offline", "Sign out of offline mode from Settings, then authenticate to manage repositories.")
                return@Column
            }
            OutlinedTextField(
                value = state.repoQuery,
                onValueChange = viewModel::setRepoQuery,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search repositories") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true
            )
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.filteredRepositories, key = { it.id }) { repo ->
                    RepositoryCard(repo, onOpen = { viewModel.selectRepository(repo) }, onClone = { viewModel.cloneRepository(repo) }, onFork = { viewModel.forkRepository(repo) })
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
private fun TerminalScreen(state: ZeusState, viewModel: MainViewModel) {
    var command by rememberSaveable { mutableStateOf("") }
    val workspace = state.selectedWorkspace
    Column(Modifier.fillMaxSize().imePadding()) {
        if (workspace == null) {
            EmptyState(Icons.Rounded.Terminal, "Select a workspace", "Open a workspace first, then commands run in its root directory.")
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                Text(workspace.name, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = viewModel::clearTerminal) { Icon(Icons.Rounded.Clear, "Clear") }
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            color = Color(0xFF101014),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items(state.terminalLines) { line ->
                    SelectionContainer {
                        Text(line, color = Color(0xFFEDEAF2), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("\$", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("git status") },
                singleLine = true
            )
            IconButton(
                onClick = { val value = command.trim(); if (value.isNotBlank()) { viewModel.runTerminal(value); command = "" } },
                enabled = command.isNotBlank() && !state.busy
            ) { Icon(Icons.Rounded.KeyboardArrowRight, "Run") }
        }
    }
}

@Composable
private fun SettingsScreen(state: ZeusState, viewModel: MainViewModel) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Text((state.user?.login ?: "Z").take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(state.user?.name ?: state.user?.login ?: "Offline user", style = MaterialTheme.typography.titleLarge)
                        Text(state.user?.bio ?: if (state.authenticated) "Connected to GitHub" else "Local mode", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SettingRow(Icons.Rounded.Bolt, "Zeus version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
        item { SettingRow(Icons.Rounded.Gavel, "OAuth callback", BuildConfig.OAUTH_CALLBACK) }
        item { SettingRow(Icons.Rounded.Save, "Token security", "AES-GCM key stored in Android Keystore") }
        item { SettingRow(Icons.Rounded.Folder, "Workspace storage", "Private app storage; export copies omit .git") }
        item {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/connections/applications"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Icon(Icons.Rounded.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("Manage GitHub authorizations") }
        }
        item {
            Button(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.authenticated) "Sign out" else "Exit offline mode")
            }
        }
        item {
            InfoCard(
                Icons.Rounded.Info,
                "Public signing key warning",
                "This project includes a disposable public release keystore only because you requested zero-setup phone builds. Anyone can sign an APK with the same identity; replace it before distributing Zeus to other people."
            )
        }
    }
}

@Composable
private fun RepositoryDialog(state: ZeusState, viewModel: MainViewModel, repo: Repository) {
    var tab by remember(repo.id) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var issueDialog by remember { mutableStateOf(false) }
    var pullDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var reviewPull by remember { mutableStateOf<PullRequest?>(null) }

    AlertDialog(
        onDismissRequest = viewModel::closeRepository,
        confirmButton = { TextButton(onClick = viewModel::closeRepository) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(repo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(repo.owner.login, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Clone") }, leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) }, onClick = { menu = false; viewModel.cloneRepository(repo) })
                        DropdownMenuItem(text = { Text("Fork") }, leadingIcon = { Icon(Icons.Rounded.ForkRight, null) }, onClick = { menu = false; viewModel.forkRepository(repo) })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; deleteConfirm = true })
                    }
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().height(520.dp)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Overview") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Pulls (${state.pullRequests.size})") })
                    Tab(tab == 2, { tab = 2 }, text = { Text("Issues (${state.issues.size})") })
                }
                when (tab) {
                    0 -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { Text(repo.description ?: "No description", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(onClick = {}, label = { Text(repo.defaultBranch) }, leadingIcon = { Icon(Icons.Rounded.AccountTree, null) })
                                AssistChip(onClick = {}, label = { Text("${repo.stars}") }, leadingIcon = { Icon(Icons.Rounded.Star, null) })
                                if (repo.private) AssistChip(onClick = {}, label = { Text("Private") })
                            }
                        }
                        item { Text("Branches", style = MaterialTheme.typography.titleMedium) }
                        items(state.branches) { branch ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AccountTree, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(branch.name, Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                                if (branch.protected) Text("Protected", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    1 -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { FilledTonalButton(onClick = { pullDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("New pull request") } }
                        if (state.pullRequests.isEmpty()) item { EmptyState(Icons.Rounded.CallMerge, "No pull requests", "Create one from a pushed branch.") }
                        items(state.pullRequests, key = { it.number }) { pull -> PullRow(pull, onMerge = { viewModel.mergePull(pull, "merge") }, onReview = { reviewPull = pull }) }
                    }
                    2 -> LazyColumn(contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { FilledTonalButton(onClick = { issueDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("New issue") } }
                        if (state.issues.isEmpty()) item { EmptyState(Icons.Rounded.ErrorOutline, "No issues", "This repository has no issues.") }
                        items(state.issues, key = { it.number }) { issue ->
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(13.dp)) {
                                    Text("#${issue.number} ${issue.title}", fontWeight = FontWeight.SemiBold)
                                    Text("${issue.state} · ${issue.user.login}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (deleteConfirm) ConfirmDialog("Delete ${repo.fullName}?", "This permanently deletes the GitHub repository. This cannot be undone.", onDismiss = { deleteConfirm = false }) {
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
    reviewPull?.let { pull -> ReviewDialog(pull, onDismiss = { reviewPull = null }) { body, event ->
        reviewPull = null
        viewModel.reviewPull(pull, body, event)
    } }
}

@Composable
private fun MetricCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun WorkspaceCard(workspace: Workspace, selected: Boolean, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Box(contentAlignment = Alignment.Center) { Icon(if (workspace.gitRepository) Icons.Rounded.Source else Icons.Rounded.Folder, null) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(workspace.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    workspace.currentBranch?.let { "Branch: $it" } ?: "Local folder",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                workspace.remoteUrl?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete workspace") }
        }
    }
}

@Composable
private fun RepositoryCard(repo: Repository, onOpen: () -> Unit, onClone: () -> Unit, onFork: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(44.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Source, null) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(repo.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (repo.private) { Spacer(Modifier.width(6.dp)); Text("Private", style = MaterialTheme.typography.labelMedium) }
                }
                Text(repo.description ?: "No description", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repo.language?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                    Text("★ ${repo.stars}", style = MaterialTheme.typography.labelMedium)
                    Text("Issues ${repo.openIssues}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Clone") }, onClick = { menu = false; onClone() }, leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) })
                    DropdownMenuItem(text = { Text("Fork") }, onClick = { menu = false; onFork() }, leadingIcon = { Icon(Icons.Rounded.ForkRight, null) })
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: FileEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !entry.directory, onClick = onOpen).padding(start = (12 + entry.depth * 14).dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (entry.directory) Icons.Rounded.Folder else Icons.Rounded.Description, null, Modifier.size(21.dp), tint = if (entry.directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(entry.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!entry.directory) Text(formatSize(entry.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Delete, "Delete", Modifier.size(18.dp)) }
    }
}

@Composable
private fun PullRow(pull: PullRequest, onMerge: () -> Unit, onReview: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Text("#${pull.number} ${pull.title}", fontWeight = FontWeight.SemiBold)
            Text("${pull.head.ref} → ${pull.base.ref} · ${pull.user.login}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onReview) { Text("Review") }
                Button(onClick = onMerge, enabled = pull.state == "open" && !pull.draft) { Icon(Icons.Rounded.CallMerge, null); Spacer(Modifier.width(6.dp)); Text("Merge") }
            }
        }
    }
}

@Composable
private fun ZeusMark(size: Int) {
    Surface(Modifier.padding(8.dp).size(size.dp), shape = RoundedCornerShape((size / 3).dp), color = MaterialTheme.colorScheme.primary) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Bolt, "Zeus", tint = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size((size * .62f).dp)) }
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp)) } }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
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
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NewWorkspaceDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var git by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New workspace") }, text = {
        Column {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(git, { git = it }); Text("Initialize Git repository") }
        }
    }, confirmButton = { Button(onClick = { onCreate(name, git) }, enabled = name.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun CloneDialog(onDismiss: () -> Unit, onClone: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Clone repository") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(url, { url = it }, label = { Text("HTTPS clone URL") }, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("Workspace name (optional)") }, singleLine = true)
        }
    }, confirmButton = { Button(onClick = { onClone(url, name.ifBlank { url.substringAfterLast('/').removeSuffix(".git") }) }, enabled = url.startsWith("http")) { Text("Clone") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun NewRepositoryDialog(onDismiss: () -> Unit, onCreate: (String, String, Boolean, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var private by remember { mutableStateOf(false) }
    var init by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create repository") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Repository name") }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Description") })
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(private, { private = it }); Spacer(Modifier.width(10.dp)); Text("Private repository") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(init, { init = it }); Text("Initialize with README") }
        }
    }, confirmButton = { Button(onClick = { onCreate(name, description, private, init) }, enabled = name.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun CommitDialog(onDismiss: () -> Unit, onCommit: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Commit all changes") }, text = { OutlinedTextField(message, { message = it }, label = { Text("Commit message") }, minLines = 3) }, confirmButton = { Button(onClick = { onCommit(message) }, enabled = message.isNotBlank()) { Text("Commit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun BranchDialog(onDismiss: () -> Unit, onCheckout: (String, Boolean) -> Unit) {
    var branch by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Switch branch") }, text = {
        Column {
            OutlinedTextField(branch, { branch = it }, label = { Text("Branch name") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(create, { create = it }); Text("Create new branch") }
        }
    }, confirmButton = { Button(onClick = { onCheckout(branch, create) }, enabled = branch.isNotBlank()) { Text("Continue") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun AdvancedGitDialog(state: ZeusState, onDismiss: () -> Unit, onBranch: () -> Unit, viewModel: MainViewModel) {
    var resetTarget by remember { mutableStateOf("HEAD") }
    var mergeBranch by remember { mutableStateOf("") }
    var revertCommit by remember { mutableStateOf("") }
    var remote by remember { mutableStateOf(state.selectedWorkspace?.remoteUrl.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Advanced Git") }, text = {
        LazyColumn(Modifier.height(460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FilledTonalButton(onClick = onBranch, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AccountTree, null); Spacer(Modifier.width(8.dp)); Text("Switch / create branch") } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilledTonalButton(onClick = viewModel::fetch, modifier = Modifier.weight(1f)) { Text("Fetch") }; FilledTonalButton(onClick = viewModel::stash, modifier = Modifier.weight(1f)) { Text("Stash") }; FilledTonalButton(onClick = viewModel::stashApply, modifier = Modifier.weight(1f)) { Text("Apply") } } }
            item { Button(onClick = { viewModel.push(true); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Force push") } }
            item { HorizontalDivider() }
            item { OutlinedTextField(mergeBranch, { mergeBranch = it }, label = { Text("Branch to merge") }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { viewModel.merge(mergeBranch); onDismiss() }, enabled = mergeBranch.isNotBlank()) { Text("Merge branch") } }
            item { OutlinedTextField(resetTarget, { resetTarget = it }, label = { Text("Hard reset target") }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { viewModel.hardReset(resetTarget); onDismiss() }) { Text("Reset --hard") } }
            item { OutlinedTextField(revertCommit, { revertCommit = it }, label = { Text("Commit to revert") }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { viewModel.revert(revertCommit); onDismiss() }, enabled = revertCommit.isNotBlank()) { Text("Revert commit") } }
            item { OutlinedTextField(remote, { remote = it }, label = { Text("Origin remote URL") }, modifier = Modifier.fillMaxWidth()); TextButton(onClick = { viewModel.setRemote(remote); onDismiss() }, enabled = remote.isNotBlank()) { Text("Update remote") } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun NewPathDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var path by remember { mutableStateOf("") }
    var directory by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create path") }, text = {
        Column {
            OutlinedTextField(path, { path = it }, label = { Text("Relative path") }, placeholder = { Text("src/main.kt") })
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(directory, { directory = it }); Text("Create directory") }
        }
    }, confirmButton = { Button(onClick = { onCreate(path, directory) }, enabled = path.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun FileEditorDialog(entry: FileEntry, viewModel: MainViewModel, onDismiss: () -> Unit) {
    var text by remember(entry.path) { mutableStateOf("") }
    var loading by remember(entry.path) { mutableStateOf(true) }
    var error by remember(entry.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.path) {
        runCatching { viewModel.readFile(entry) }
            .onSuccess { text = it }
            .onFailure { error = it.message }
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Box(Modifier.fillMaxWidth().height(520.dp)) {
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    else -> OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        label = { Text(entry.path) }
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { viewModel.saveFile(entry, text); onDismiss() }, enabled = !loading && error == null) { Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(6.dp)); Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TextBodyDialog(titleText: String, titleLabel: String, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(titleText) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(titleLabel) })
            OutlinedTextField(body, { body = it }, label = { Text("Description") }, minLines = 5)
        }
    }, confirmButton = { Button(onClick = { onSubmit(title, body) }, enabled = title.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun PullRequestDialog(defaultBase: String, onDismiss: () -> Unit, onSubmit: (String, String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var head by remember { mutableStateOf("") }
    var base by remember { mutableStateOf(defaultBase) }
    var body by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("New pull request") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Title") })
            OutlinedTextField(head, { head = it }, label = { Text("Head branch") })
            OutlinedTextField(base, { base = it }, label = { Text("Base branch") })
            OutlinedTextField(body, { body = it }, label = { Text("Description") }, minLines = 3)
        }
    }, confirmButton = { Button(onClick = { onSubmit(title, head, base, body) }, enabled = title.isNotBlank() && head.isNotBlank() && base.isNotBlank()) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ReviewDialog(pull: PullRequest, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var body by remember { mutableStateOf("") }
    var event by remember { mutableStateOf("COMMENT") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Review #${pull.number}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(body, { body = it }, label = { Text("Review comments") }, minLines = 5)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("COMMENT", "APPROVE", "REQUEST_CHANGES").forEach { value -> FilterChip(selected = event == value, onClick = { event = value }, label = { Text(value.replace('_', ' ')) }) }
            }
        }
    }, confirmButton = { Button(onClick = { onSubmit(body, event) }, enabled = body.isNotBlank() || event == "APPROVE") { Text("Submit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { Button(onClick = onConfirm) { Text("Confirm") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun tabTitle(tab: MainTab) = when (tab) {
    MainTab.HOME -> "Home"
    MainTab.WORKSPACES -> "Workspaces"
    MainTab.GITHUB -> "GitHub"
    MainTab.TERMINAL -> "Terminal"
    MainTab.SETTINGS -> "Settings"
}

private fun tabIcon(tab: MainTab) = when (tab) {
    MainTab.HOME -> Icons.Rounded.Home
    MainTab.WORKSPACES -> Icons.Rounded.Folder
    MainTab.GITHUB -> Icons.Rounded.Source
    MainTab.TERMINAL -> Icons.Rounded.Terminal
    MainTab.SETTINGS -> Icons.Rounded.Settings
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}
