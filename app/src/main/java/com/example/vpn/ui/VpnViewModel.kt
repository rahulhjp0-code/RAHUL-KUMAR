package com.example.vpn.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vpn.data.VpnRepository
import com.example.vpn.model.AppInfo
import com.example.vpn.model.VpnErrorType
import com.example.vpn.model.VpnLogEntry
import com.example.vpn.model.VpnServer
import com.example.vpn.model.VpnSettings
import com.example.vpn.model.VpnState
import com.example.vpn.service.MyVpnService
import com.example.vpn.ui.screens.SiteTestItem
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VpnUiState(
    val vpnState: VpnState = VpnState.Disconnected(),
    val selectedServer: VpnServer? = null,
    val servers: List<VpnServer> = emptyList(),
    val settings: VpnSettings = VpnSettings(),
    val durationSeconds: Long = 0L,
    val isTestingPings: Boolean = false,
    val installedApps: List<AppInfo> = emptyList(),
    val siteTests: List<SiteTestItem> = listOf(
        SiteTestItem("Google", "https://www.google.com"),
        SiteTestItem("Cloudflare", "https://1.1.1.1/help"),
        SiteTestItem("Wikipedia", "https://www.wikipedia.org"),
        SiteTestItem("Apple CDN", "https://captive.apple.com/hotspot-detect.html")
    ),
    val isTestingSites: Boolean = false
)

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class RequestVpnPermission(val onGranted: () -> Unit) : UiEvent
}

private data class Four(
    val state: VpnState,
    val server: VpnServer?,
    val servers: List<VpnServer>,
    val config: VpnSettings
)

private data class ExtraState(
    val duration: Long,
    val testingPings: Boolean,
    val apps: List<AppInfo>,
    val sites: List<SiteTestItem>,
    val testingSites: Boolean
)

class VpnViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VpnRepository.getInstance(application)

    val vpnState: StateFlow<VpnState> = MyVpnService.vpnStateFlow
    val allServers: StateFlow<List<VpnServer>> = repository.allServers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    val selectedServer: StateFlow<VpnServer?> = repository.selectedServer
    val settings: StateFlow<VpnSettings> = repository.settings
    val logs: StateFlow<List<VpnLogEntry>> = repository.logs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _durationSeconds = MutableStateFlow(0L)
    val durationSeconds: StateFlow<Long> = _durationSeconds.asStateFlow()

    private val _isTestingPings = MutableStateFlow(false)
    val isTestingPings: StateFlow<Boolean> = _isTestingPings.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _siteTests = MutableStateFlow(
        listOf(
            SiteTestItem("Google", "https://www.google.com"),
            SiteTestItem("Cloudflare", "https://1.1.1.1/help"),
            SiteTestItem("Wikipedia", "https://www.wikipedia.org"),
            SiteTestItem("Apple CDN", "https://captive.apple.com/hotspot-detect.html")
        )
    )
    val siteTests: StateFlow<List<SiteTestItem>> = _siteTests.asStateFlow()

    private val _isTestingSites = MutableStateFlow(false)
    val isTestingSites: StateFlow<Boolean> = _isTestingSites.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private var durationTimerJob: Job? = null

    val uiState: StateFlow<VpnUiState> = combine(
        combine(vpnState, selectedServer, allServers, settings) { state, server, servers, config ->
            Four(state, server ?: servers.firstOrNull(), servers, config)
        },
        combine(durationSeconds, isTestingPings, installedApps, siteTests, isTestingSites) { duration, testing, apps, sites, testingSites ->
            ExtraState(duration, testing, apps, sites, testingSites)
        }
    ) { four, extra ->
        VpnUiState(
            vpnState = four.state,
            selectedServer = four.server,
            servers = four.servers,
            settings = four.config,
            durationSeconds = extra.duration,
            isTestingPings = extra.testingPings,
            installedApps = extra.apps,
            siteTests = extra.sites,
            isTestingSites = extra.testingSites
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VpnUiState()
    )

    init {
        // Monitor VPN state transitions for duration tracking
        viewModelScope.launch {
            vpnState.collect { state ->
                when (state) {
                    is VpnState.Connected -> {
                        startDurationTimer(state.connectedAtMillis)
                    }
                    else -> {
                        stopDurationTimer()
                    }
                }
            }
        }

        // Load installed apps for split tunneling in background
        viewModelScope.launch(Dispatchers.IO) {
            val apps = repository.getInstalledApps()
            _installedApps.value = apps
        }
    }

    private fun startDurationTimer(startTimeMillis: Long) {
        durationTimerJob?.cancel()
        durationTimerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000
                _durationSeconds.value = maxOf(0L, elapsed)
                delay(1000)
            }
        }
    }

    private fun stopDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
        _durationSeconds.value = 0L
    }

    fun onConnectToggle(context: Context, requestPermissionCallback: () -> Unit) {
        val currentState = vpnState.value
        when (currentState) {
            is VpnState.Connected, is VpnState.Connecting -> {
                disconnect(context)
            }
            is VpnState.Disconnected, is VpnState.Error -> {
                val server = selectedServer.value ?: allServers.value.firstOrNull()
                if (server == null) {
                    viewModelScope.launch {
                        _uiEvents.emit(UiEvent.ShowToast("No server selected"))
                    }
                    return
                }
                requestPermissionCallback()
            }
        }
    }

    fun connectWithPermission(context: Context) {
        val server = selectedServer.value ?: allServers.value.firstOrNull() ?: return
        MyVpnService.startService(context, server)
    }

    fun disconnect(context: Context) {
        MyVpnService.stopService(context)
    }

    fun selectServer(server: VpnServer) {
        viewModelScope.launch {
            repository.selectServer(server)
        }
    }

    fun addCustomServer(server: VpnServer) {
        viewModelScope.launch {
            repository.addCustomServer(server)
            _uiEvents.emit(UiEvent.ShowToast("Added server: ${server.name}"))
        }
    }

    fun updateServer(server: VpnServer) {
        viewModelScope.launch {
            repository.updateServer(server)
            _uiEvents.emit(UiEvent.ShowToast("Updated server: ${server.name}"))
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            repository.deleteServer(serverId)
            _uiEvents.emit(UiEvent.ShowToast("Server removed"))
        }
    }

    fun toggleFavorite(serverId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(serverId, isFavorite)
        }
    }

    fun testServerPings() {
        if (_isTestingPings.value) return
        viewModelScope.launch {
            _isTestingPings.value = true
            val currentServers = allServers.value
            for (server in currentServers) {
                repository.testPing(server)
            }
            _isTestingPings.value = false
            _uiEvents.emit(UiEvent.ShowToast("Latency test completed"))
        }
    }

    fun updateSettings(newSettings: VpnSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
            _uiEvents.emit(UiEvent.ShowToast("Settings updated"))
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiEvents.emit(UiEvent.ShowToast("Logs cleared"))
        }
    }

    fun runSiteConnectivityTests() {
        if (_isTestingSites.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isTestingSites.value = true
            val currentList = _siteTests.value.toMutableList()

            val updatedList = currentList.map { item ->
                try {
                    val start = System.currentTimeMillis()
                    val url = URL(item.url)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.instanceFollowRedirects = true
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    val elapsed = System.currentTimeMillis() - start
                    conn.disconnect()

                    item.copy(
                        latencyMs = elapsed,
                        isSuccess = (code in 200..399 || code == 204),
                        statusCode = code
                    )
                } catch (e: Exception) {
                    item.copy(
                        latencyMs = null,
                        isSuccess = false,
                        statusCode = null
                    )
                }
            }

            _siteTests.value = updatedList
            _isTestingSites.value = false
            _uiEvents.emit(UiEvent.ShowToast("Site connectivity test completed"))
        }
    }

    fun dismissError() {
        if (vpnState.value is VpnState.Error) {
            // Reset to Disconnected
            MyVpnService.stopService(getApplication())
        }
    }
}
