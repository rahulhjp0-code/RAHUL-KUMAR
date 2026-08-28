package com.example.vpn.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.Cyan80
import com.example.ui.theme.EmeraldConnected
import com.example.vpn.model.VpnServer
import com.example.vpn.model.VpnState
import com.example.vpn.ui.screens.BackendGuideBottomSheet
import com.example.vpn.ui.screens.BrowserScreen
import com.example.vpn.ui.screens.CustomServerDialog
import com.example.vpn.ui.screens.DashboardScreen
import com.example.vpn.ui.screens.LogsScreen
import com.example.vpn.ui.screens.ServerListScreen
import com.example.vpn.ui.screens.SettingsScreen
import com.example.vpn.ui.screens.SplitTunnelBottomSheet

enum class NavTab(val title: String, val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector, val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("VPN", Icons.Filled.Shield, Icons.Outlined.Shield),
    BROWSER("Web Test", Icons.Filled.Language, Icons.Outlined.Language),
    SERVERS("Servers", Icons.Filled.Public, Icons.Outlined.Public),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    LOGS("Logs", Icons.Filled.Article, Icons.Outlined.Article)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(
    viewModel: VpnViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(NavTab.DASHBOARD) }
    var showCustomServerDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<VpnServer?>(null) }
    var showBackendGuideSheet by remember { mutableStateOf(false) }
    var showSplitTunnelSheet by remember { mutableStateOf(false) }

    // Prepare Android VpnService permission launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.connectWithPermission(context)
        } else {
            Toast.makeText(context, "VPN Permission was denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to initiate connection with permission check
    val initiateConnectWithPermission = {
        val prepareIntent: Intent? = VpnService.prepare(context)
        if (prepareIntent != null) {
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            viewModel.connectWithPermission(context)
        }
    }

    // Handle UI events (Toasts, etc.)
    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.RequestVpnPermission -> {
                    initiateConnectWithPermission()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SECURE VPN",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showBackendGuideSheet = true },
                        modifier = Modifier.testTag("backend_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Backend Setup Guide",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                NavTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                    )
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabContent"
            ) { tab ->
                when (tab) {
                    NavTab.DASHBOARD -> DashboardScreen(
                        vpnState = uiState.vpnState,
                        selectedServer = uiState.selectedServer,
                        durationSeconds = uiState.durationSeconds,
                        onConnectToggle = {
                            viewModel.onConnectToggle(context) {
                                initiateConnectWithPermission()
                            }
                        },
                        onNavigateToServers = { currentTab = NavTab.SERVERS },
                        onOpenBrowser = { currentTab = NavTab.BROWSER },
                        onOpenBackendGuide = { showBackendGuideSheet = true },
                        onDismissError = { viewModel.dismissError() }
                    )
                    NavTab.BROWSER -> BrowserScreen(
                        vpnState = uiState.vpnState,
                        siteTests = uiState.siteTests,
                        isTestingSites = uiState.isTestingSites,
                        onRunSiteTests = { viewModel.runSiteConnectivityTests() }
                    )
                    NavTab.SERVERS -> ServerListScreen(
                        servers = uiState.servers,
                        selectedServer = uiState.selectedServer,
                        isTestingPings = uiState.isTestingPings,
                        onSelectServer = { server ->
                            viewModel.selectServer(server)
                            currentTab = NavTab.DASHBOARD
                        },
                        onToggleFavorite = { id, isFav -> viewModel.toggleFavorite(id, isFav) },
                        onDeleteServer = { id -> viewModel.deleteServer(id) },
                        onTestPings = { viewModel.testServerPings() },
                        onAddCustomServer = {
                            serverToEdit = null
                            showCustomServerDialog = true
                        }
                    )
                    NavTab.SETTINGS -> SettingsScreen(
                        settings = uiState.settings,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onOpenSplitTunnel = { showSplitTunnelSheet = true },
                        onOpenBackendGuide = { showBackendGuideSheet = true },
                        onClearLogs = { viewModel.clearLogs() }
                    )
                    NavTab.LOGS -> LogsScreen(
                        logs = logs,
                        onClearLogs = { viewModel.clearLogs() }
                    )
                }
            }
        }

        // Custom Server Add/Edit Dialog
        if (showCustomServerDialog) {
            CustomServerDialog(
                initialServer = serverToEdit,
                onDismiss = {
                    showCustomServerDialog = false
                    serverToEdit = null
                },
                onSave = { server ->
                    if (serverToEdit == null) {
                        viewModel.addCustomServer(server)
                    } else {
                        viewModel.updateServer(server)
                    }
                    showCustomServerDialog = false
                    serverToEdit = null
                }
            )
        }

        // Backend Setup Guide BottomSheet
        if (showBackendGuideSheet) {
            BackendGuideBottomSheet(
                onDismiss = { showBackendGuideSheet = false }
            )
        }

        // Split Tunnel BottomSheet
        if (showSplitTunnelSheet) {
            SplitTunnelBottomSheet(
                installedApps = uiState.installedApps,
                currentSettings = uiState.settings,
                onSaveSettings = { viewModel.updateSettings(it) },
                onDismiss = { showSplitTunnelSheet = false }
            )
        }
    }
}
