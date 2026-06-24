package com.obdiitools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.obdiitools.ui.screens.BluetoothScreen
import com.obdiitools.ui.screens.CanMonitorScreen
import com.obdiitools.ui.screens.DiagnosticsScreen
import com.obdiitools.ui.screens.CVTScreen
import com.obdiitools.ui.screens.CustomPidScreen
import com.obdiitools.ui.screens.DashboardScreen
import com.obdiitools.ui.screens.DeepScanScreen
import com.obdiitools.ui.screens.DTCScreen
import com.obdiitools.ui.screens.FreezeFrameScreen
import com.obdiitools.ui.screens.GlossaryScreen
import com.obdiitools.ui.screens.HomeScreen
import com.obdiitools.ui.screens.LiveDataScreen
import com.obdiitools.ui.screens.ReadinessScreen
import com.obdiitools.ui.screens.SessionMapScreen
import com.obdiitools.ui.screens.SessionScreen
import com.obdiitools.ui.screens.SettingsScreen
import com.obdiitools.ui.screens.UdsScreen
import com.obdiitools.ui.theme.BackgroundCard
import com.obdiitools.ui.theme.BackgroundDeep
import com.obdiitools.ui.theme.NeonCyan
import com.obdiitools.ui.theme.NeonRed
import com.obdiitools.ui.theme.OBDIITheme
import com.obdiitools.ui.theme.SurfaceBorder
import com.obdiitools.ui.theme.TextSecondary
import com.obdiitools.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

sealed class NavScreen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    object Home      : NavScreen("home",      "Home",      Icons.Filled.Home,      Icons.Outlined.Home)
    object Dashboard : NavScreen("dashboard", "Dashboard", Icons.Filled.Speed,     Icons.Outlined.Speed)
    object DTC       : NavScreen("dtc",       "Faults",    Icons.Filled.Warning,   Icons.Outlined.Warning)
    object Bluetooth : NavScreen("bluetooth", "Bluetooth", Icons.Filled.Bluetooth, Icons.Outlined.Bluetooth)
    object Settings  : NavScreen("settings",  "Settings",  Icons.Filled.Settings,  Icons.Outlined.Settings)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OBDIITheme {
                val prefs by viewModel.userPreferences.collectAsState()
                DisposableEffect(prefs.keepScreenOn) {
                    if (prefs.keepScreenOn) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                OBDIIApp(viewModel)
            }
        }
    }
}

@Composable
fun OBDIIApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val dtcList by viewModel.dtcList.collectAsState()

    val tabs = listOf(
        NavScreen.Home,
        NavScreen.Dashboard,
        NavScreen.DTC,
        NavScreen.Bluetooth,
        NavScreen.Settings,
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDeep),
        containerColor = BackgroundDeep,
        bottomBar = {
            OBDBottomNav(
                tabs = tabs,
                navController = navController,
                dtcCount = dtcList.size,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavScreen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition  = { fadeOut(tween(180)) },
        ) {
            composable(NavScreen.Home.route) {
                HomeScreen(
                    viewModel,
                    onNavigateToCanMonitor  = { navController.navigate("can_monitor") },
                    onNavigateToCvtMonitor  = { navController.navigate("cvt_monitor") },
                    onNavigateToUdsReader   = { navController.navigate("uds_reader") },
                    onNavigateToReadiness   = { navController.navigate("readiness") },
                    onNavigateToFreezeFrame = { navController.navigate("freeze_frame") },
                    onNavigateToSessions    = { navController.navigate("sessions") },
                    onNavigateToCustomPids  = { navController.navigate("custom_pids") },
                    onNavigateToDeepScan    = { navController.navigate("deep_scan") },
                    onNavigateToGlossary    = { navController.navigate("glossary") },
                )
            }
            composable(NavScreen.Dashboard.route) {
                DashboardScreen(viewModel, onNavigateToLiveData = { navController.navigate("live_data") })
            }
            composable(NavScreen.DTC.route) {
                DTCScreen(
                    viewModel,
                    onNavigateToFreezeFrame = { navController.navigate("freeze_frame") },
                    onNavigateToReadiness   = { navController.navigate("readiness") },
                )
            }
            composable(NavScreen.Bluetooth.route) { BluetoothScreen(viewModel) }
            composable(NavScreen.Settings.route)  {
                SettingsScreen(viewModel, onNavigateToDiagnostics = { navController.navigate("diagnostics") })
            }
            composable("live_data") {
                LiveDataScreen(onNavigateBack = { navController.navigateUp() })
            }
            composable("can_monitor") {
                CanMonitorScreen(onNavigateBack = { navController.navigateUp() })
            }
            composable("cvt_monitor") {
                CVTScreen(onNavigateBack = { navController.navigateUp() })
            }
            composable("uds_reader") {
                UdsScreen(onNavigateBack = { navController.navigateUp() })
            }
            composable("readiness") {
                ReadinessScreen(onBack = { navController.navigateUp() })
            }
            composable("freeze_frame") {
                FreezeFrameScreen(onBack = { navController.navigateUp() })
            }
            composable("sessions") {
                SessionScreen(
                    onBack          = { navController.navigateUp() },
                    onNavigateToMap = { id -> navController.navigate("session_map/$id") },
                )
            }
            composable(
                route     = "session_map/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { backStackEntry ->
                SessionMapScreen(
                    sessionId = backStackEntry.arguments!!.getLong("sessionId"),
                    onBack    = { navController.navigateUp() },
                )
            }
            composable("custom_pids") {
                CustomPidScreen(onBack = { navController.navigateUp() })
            }
            composable("deep_scan") {
                DeepScanScreen(onNavigateBack = { navController.navigateUp() })
            }
            composable("glossary") {
                GlossaryScreen(onNavigateBack = { navController.navigateUp() })
            }
            composable("diagnostics") {
                DiagnosticsScreen(onBack = { navController.navigateUp() })
            }
        }
    }
}

@Composable
private fun OBDBottomNav(
    tabs: List<NavScreen>,
    navController: androidx.navigation.NavHostController,
    dtcCount: Int,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .navigationBarsPadding(),
        containerColor = BackgroundCard,
        tonalElevation = 0.dp,
    ) {
        tabs.forEach { tab ->
            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab is NavScreen.DTC && dtcCount > 0) {
                                Badge(containerColor = NeonRed) {
                                    Text(
                                        "$dtcCount",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                        )
                    }
                },
                label = {
                    Text(
                        text = tab.label,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor       = NeonCyan,
                    selectedTextColor       = NeonCyan,
                    unselectedIconColor     = TextSecondary,
                    unselectedTextColor     = TextSecondary,
                    indicatorColor          = NeonCyan.copy(alpha = 0.12f),
                ),
            )
        }
    }
}
