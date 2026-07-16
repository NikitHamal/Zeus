
package com.zeus.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zeus.data.auth.AuthManager
import com.zeus.ui.screens.auth.LoginScreen
import com.zeus.ui.screens.dashboard.DashboardScreen
import com.zeus.ui.screens.editor.EditorScreen
import com.zeus.ui.screens.files.FileBrowserScreen
import com.zeus.ui.screens.git.BranchManagerScreen
import com.zeus.ui.screens.git.CommitScreen
import com.zeus.ui.screens.git.DiffScreen
import com.zeus.ui.screens.git.HistoryScreen
import com.zeus.ui.screens.pr.PRListScreen
import com.zeus.ui.screens.repo.CreateRepoScreen
import com.zeus.ui.screens.repos.RepoDetailScreen
import com.zeus.ui.screens.repos.RepoListScreen
import com.zeus.ui.screens.settings.SettingsScreen
import com.zeus.ui.screens.terminal.TerminalScreen
import dagger.hilt.android.EntryPointAccessors
import android.content.Context
import androidx.compose.ui.platform.LocalContext

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object RepoList : Screen("repo_list")
    object RepoDetail : Screen("repo_detail/{fullName}") { fun create(fullName: String) = "repo_detail/$fullName" }
    object FileBrowser : Screen("file_browser/{localPath}") { fun create(p: String) = "file_browser/$p" }
    object Editor : Screen("editor?path={path}&repo={repo}") { fun create(path: String, repo: String) = "editor?path=$path&repo=$repo" }
    object Commit : Screen("commit/{localPath}") { fun create(p: String) = "commit/$p" }
    object Branches : Screen("branches/{localPath}") { fun create(p: String) = "branches/$p" }
    object History : Screen("history/{localPath}") { fun create(p: String) = "history/$p" }
    object Diff : Screen("diff/{localPath}?file={file}") { fun create(lp: String, file: String) = "diff/$lp?file=$file" }
    object Terminal : Screen("terminal/{localPath}") { fun create(p: String) = "terminal/$p" }
    object CreateRepo : Screen("create_repo")
    object PRList : Screen("pr_list/{fullName}") { fun create(fn: String) = "pr_list/$fn" }
    object Settings : Screen("settings")
}

@Composable
fun ZeusNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val authManagerEntry = remember {
        EntryPointAccessors.fromApplication(context, AuthEntryPoint::class.java).authManager()
    }
    var startDestination by rememberSaveable { mutableStateOf(Screen.Login.route) }
    LaunchedEffect(Unit) {
        val token = authManagerEntry.getToken()
        startDestination = if (token.isNullOrBlank()) Screen.Login.route else Screen.Dashboard.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Login.route) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Screen.Dashboard.route) { popUpTo(Screen.Login.route) { inclusive = true } }
            })
        }
        composable(Screen.Dashboard.route) { DashboardScreen(navController) }
        composable(Screen.RepoList.route) { RepoListScreen(navController) }
        composable(Screen.RepoDetail.route, arguments = listOf(navArgument("fullName"){ type = NavType.StringType })) { backStack ->
            val fullName = backStack.arguments?.getString("fullName") ?: ""
            RepoDetailScreen(navController, fullName)
        }
        composable(Screen.FileBrowser.route, arguments = listOf(navArgument("localPath"){ type = NavType.StringType })) {
            val path = it.arguments?.getString("localPath") ?: ""
            FileBrowserScreen(navController, path)
        }
        composable(Screen.Editor.route, arguments = listOf(navArgument("path"){ type= NavType.StringType }, navArgument("repo"){ type= NavType.StringType })) { bs ->
            val path = bs.arguments?.getString("path") ?: ""
            val repo = bs.arguments?.getString("repo") ?: ""
            EditorScreen(navController, path, repo)
        }
        composable(Screen.Commit.route) { FileBrowserScreen(navController, it.arguments?.getString("localPath")?:"") } // placeholder routed correctly below
        composable("commit/{localPath}", arguments = listOf(navArgument("localPath"){type=NavType.StringType})) {
            CommitScreen(navController, it.arguments?.getString("localPath")?:"")
        }
        composable(Screen.Branches.route) { BranchManagerScreen(navController, it.arguments?.getString("localPath")?:"") }
        composable(Screen.History.route) { HistoryScreen(navController, it.arguments?.getString("localPath")?:"") }
        composable(Screen.Diff.route, arguments = listOf(navArgument("localPath"){type=NavType.StringType}, navArgument("file"){type=NavType.StringType; defaultValue=""})) {
            DiffScreen(navController, it.arguments?.getString("localPath")?:"", it.arguments?.getString("file")?:"")
        }
        composable(Screen.Terminal.route) { TerminalScreen(navController, it.arguments?.getString("localPath")?:"") }
        composable(Screen.CreateRepo.route) { CreateRepoScreen(navController) }
        composable(Screen.PRList.route) { PRListScreen(navController, it.arguments?.getString("fullName")?:"") }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AuthEntryPoint {
    fun authManager(): com.zeus.data.auth.AuthManager
}
