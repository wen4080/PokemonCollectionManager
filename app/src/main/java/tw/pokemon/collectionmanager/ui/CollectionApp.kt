package tw.pokemon.collectionmanager.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import tw.pokemon.collectionmanager.BuildConfig
import tw.pokemon.collectionmanager.data.local.ThemeMode

private object Routes {
    const val HOME = "home"
    const val ACCOUNTS = "accounts"
    const val OVERVIEW = "overview"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ACCOUNT = "account/{accountId}"
    const val VARIANT = "variant/{variantId}/{accountId}"
}

private data class BottomDestination(val route: String, val label: String, val glyph: String)

@Composable
fun CollectionApp(viewModel: CollectionViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    CollectionTheme(themeMode) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val messages = viewModel.messages

        LaunchedEffect(messages) {
            messages.collect { snackbarHostState.showSnackbar(it) }
        }

        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) viewModel.exportBackup(uri)
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importBackup(uri)
        }
        val masterLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.importMasterData(uri)
        }

        val destinations = remember {
            listOf(
                BottomDestination(Routes.HOME, "首頁", "⌂"),
                BottomDestination(Routes.ACCOUNTS, "帳號", "◎"),
                BottomDestination(Routes.OVERVIEW, "總覽", "▦"),
                BottomDestination(Routes.SEARCH, "搜尋", "⌕"),
                BottomDestination(Routes.SETTINGS, "設定", "⚙"),
            )
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    val current = navBackStackEntry?.destination?.route
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination.route,
                            onClick = { navController.navigate(destination.route) { launchSingleTop = true } },
                            icon = { Text(destination.glyph) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenAccount = { id -> if (id.isBlank()) navController.navigate(Routes.ACCOUNTS) else navController.navigate("account/$id") },
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        onOpenVariant = { id, accountId -> navController.navigate("variant/$id/${accountId ?: "all"}") },
                    )
                }
                composable(Routes.ACCOUNTS) {
                    AccountsScreen(
                        viewModel = viewModel,
                        onOpenAccount = { id -> navController.navigate("account/$id") },
                    )
                }
                composable(Routes.OVERVIEW) {
                    OverviewScreen(
                        viewModel = viewModel,
                        onOpenVariant = { id -> navController.navigate("variant/$id/all") },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        viewModel = viewModel,
                        onOpenVariant = { id -> navController.navigate("variant/$id/all") },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        viewModel = viewModel,
                        appVersion = BuildConfig.VERSION_NAME,
                        onExport = { exportLauncher.launch("pokemon_collection_backup.json") },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                        onImportMasterData = { masterLauncher.launch(arrayOf("application/json", "text/json", "*/*")) },
                    )
                }
                composable(
                    route = Routes.ACCOUNT,
                    arguments = listOf(navArgument("accountId") { type = NavType.StringType }),
                ) { entry ->
                    val id = entry.arguments?.getString("accountId").orEmpty()
                    AccountCollectionScreen(
                        viewModel = viewModel,
                        accountId = id,
                        onBack = { navController.popBackStack() },
                        onOpenVariant = { variantId -> navController.navigate("variant/$variantId/$id") },
                    )
                }
                composable(
                    route = Routes.VARIANT,
                    arguments = listOf(
                        navArgument("variantId") { type = NavType.StringType },
                        navArgument("accountId") { type = NavType.StringType },
                    ),
                ) { entry ->
                    VariantDetailScreen(
                        viewModel = viewModel,
                        variantId = entry.arguments?.getString("variantId").orEmpty(),
                        accountId = entry.arguments?.getString("accountId")?.takeUnless { it == "all" },
                        onBack = { navController.popBackStack() },
                        onOpenAccount = { id -> navController.navigate("account/$id") },
                    )
                }
            }
        }
    }
}
