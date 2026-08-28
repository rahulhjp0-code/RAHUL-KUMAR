package com.example.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.vpn.data.VpnRepository
import com.example.vpn.model.VpnErrorType
import com.example.vpn.model.VpnLogEntry
import com.example.vpn.model.VpnServer
import com.example.vpn.model.VpnSettings
import com.example.vpn.model.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MyVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnelJob: Job? = null
    private var statsJob: Job? = null

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelSocket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)

    private val totalUploadBytes = AtomicLong(0L)
    private val totalDownloadBytes = AtomicLong(0L)
    private var lastUploadSnapshot = 0L
    private var lastDownloadSnapshot = 0L
    private var connectionStartTime = 0L

    private var activeServer: VpnServer? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private lateinit var repository: VpnRepository
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        repository = VpnRepository.getInstance(applicationContext)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        registerNetworkCallback()
        log(VpnLogEntry.LogLevel.INFO, TAG, "VpnService created successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_CONNECT
        when (action) {
            ACTION_CONNECT -> {
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                val serverHost = intent?.getStringExtra(EXTRA_SERVER_HOST) ?: "127.0.0.1"
                val serverPort = intent?.getIntExtra(EXTRA_SERVER_PORT, 1194) ?: 1194
                val serverName = intent?.getStringExtra(EXTRA_SERVER_NAME) ?: "VPN Server"
                val countryCode = intent?.getStringExtra(EXTRA_COUNTRY_CODE) ?: "UN"
                val countryName = intent?.getStringExtra(EXTRA_COUNTRY_NAME) ?: "Unknown"
                val flagEmoji = intent?.getStringExtra(EXTRA_FLAG_EMOJI) ?: "🛡️"
                val tunnelIp = intent?.getStringExtra(EXTRA_TUNNEL_IP) ?: "10.8.0.2"
                val mtu = intent?.getIntExtra(EXTRA_MTU, 1420) ?: 1420
                val dnsList = intent?.getStringArrayListExtra(EXTRA_DNS) ?: arrayListOf("1.1.1.1", "8.8.8.8")
                val isCustom = intent?.getBooleanExtra(EXTRA_IS_CUSTOM, false) ?: false

                val server = VpnServer(
                    id = serverId ?: "active_session",
                    name = serverName,
                    countryCode = countryCode,
                    countryName = countryName,
                    flagEmoji = flagEmoji,
                    host = serverHost,
                    port = serverPort,
                    tunnelIpv4 = tunnelIp,
                    dnsServers = dnsList,
                    mtu = mtu,
                    isCustom = isCustom
                )

                startVpn(server)
            }
            ACTION_DISCONNECT -> {
                stopVpn(reason = "User requested disconnect")
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpn(server: VpnServer) {
        if (isRunning.get()) {
            stopVpnInternal(reason = "Switching server connection")
        }

        activeServer = server
        connectionStartTime = System.currentTimeMillis()
        totalUploadBytes.set(0L)
        totalDownloadBytes.set(0L)
        lastUploadSnapshot = 0L
        lastDownloadSnapshot = 0L

        updateState(VpnState.Connecting(server, "Configuring virtual network interface..."))
        log(VpnLogEntry.LogLevel.INFO, TAG, "Initiating VPN connection to ${server.name} (${server.host}:${server.port})")

        // Start Foreground Notification immediately
        val initialNotification = buildNotification("Connecting to ${server.name}...", "0 B/s • Initializing")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        serviceScope.launch {
            try {
                val settings = repository.settings.value
                establishTunnel(server, settings)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to establish VPN tunnel", e)
                val errorMsg = e.localizedMessage ?: "Failed to initialize TUN interface"
                updateState(VpnState.Error(
                    message = "VPN Connection Failed",
                    technicalDetails = errorMsg,
                    errorType = VpnErrorType.TUNNEL_CREATION_FAILED
                ))
                log(VpnLogEntry.LogLevel.ERROR, TAG, "Tunnel creation error: $errorMsg")
                stopVpnInternal(reason = "Tunnel creation failed: $errorMsg")
            }
        }
    }

    private var packetEngine: VpnPacketEngine? = null

    private suspend fun establishTunnel(server: VpnServer, settings: VpnSettings) = withContext(Dispatchers.IO) {
        log(VpnLogEntry.LogLevel.INFO, TAG, "Configuring VpnService.Builder (Mode: ${settings.routingMode.title})...")

        val builder = Builder()
        builder.setSession("${server.name} (${server.protocol.displayName})")
        builder.setMtu(server.mtu)

        // Configure Virtual Interface IP
        try {
            builder.addAddress(server.tunnelIpv4, server.tunnelPrefixLength)
            log(VpnLogEntry.LogLevel.DEBUG, TAG, "Assigned virtual IP: ${server.tunnelIpv4}/${server.tunnelPrefixLength}")
        } catch (e: Exception) {
            builder.addAddress("10.8.0.2", 24)
            log(VpnLogEntry.LogLevel.WARN, TAG, "Defaulted to fallback virtual IP 10.8.0.2/24")
        }

        // Configure Routing based on Selected Routing Mode
        val activeDns = settings.activeDnsList.ifEmpty { server.dnsServers }
        when (settings.routingMode) {
            VpnSettings.RoutingMode.SMART_DNS_SHIELD -> {
                // Route all DNS server queries through TUN interface for encrypted DNS & protection
                val dnsTargets = (activeDns + listOf("1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", "94.140.14.14", "94.140.15.15")).distinct()
                dnsTargets.forEach { dnsIp ->
                    try {
                        builder.addRoute(dnsIp, 32)
                        log(VpnLogEntry.LogLevel.DEBUG, TAG, "Smart Shield Route: $dnsIp/32")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add route for $dnsIp", e)
                    }
                }
                log(VpnLogEntry.LogLevel.INFO, TAG, "Smart DNS Shield active: DNS queries encrypted, web traffic unblocked & fast")
            }
            VpnSettings.RoutingMode.FULL_TUNNEL -> {
                // Full IPv4 tunnel
                builder.addRoute("0.0.0.0", 0)
                log(VpnLogEntry.LogLevel.INFO, TAG, "Full IPv4 Tunnel active: 0.0.0.0/0 routed")
            }
        }

        // Add DNS Servers
        activeDns.forEach { dns ->
            try {
                builder.addDnsServer(dns)
                log(VpnLogEntry.LogLevel.DEBUG, TAG, "Configured DNS Server: $dns")
            } catch (e: Exception) {
                Log.w(TAG, "Could not add DNS $dns", e)
            }
        }

        // Configure Split Tunneling if enabled
        when (settings.splitTunnelMode) {
            VpnSettings.SplitTunnelMode.EXCLUDE_SELECTED -> {
                settings.excludedAppPackages.forEach { pkg ->
                    try {
                        builder.addDisallowedApplication(pkg)
                        log(VpnLogEntry.LogLevel.VERBOSE, TAG, "Split Tunnel: Bypassing package $pkg")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to bypass app $pkg", e)
                    }
                }
            }
            VpnSettings.SplitTunnelMode.INCLUDE_ONLY_SELECTED -> {
                if (settings.includedAppPackages.isNotEmpty()) {
                    settings.includedAppPackages.forEach { pkg ->
                        try {
                            builder.addAllowedApplication(pkg)
                            log(VpnLogEntry.LogLevel.VERBOSE, TAG, "Split Tunnel: Routing package $pkg")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to route app $pkg", e)
                        }
                    }
                }
            }
            VpnSettings.SplitTunnelMode.DISABLED -> {
                // All apps routed
            }
        }

        // Configure activity intent when user clicks VPN notification
        val configIntent = Intent(this@MyVpnService, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this@MyVpnService,
            0,
            configIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setConfigureIntent(pendingIntent)

        if (settings.killSwitchEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // Establish the descriptor
        val pfd = builder.establish()
        if (pfd == null) {
            throw IOException("VpnService.Builder.establish() returned null. Permission might have been revoked.")
        }
        vpnInterface = pfd
        isRunning.set(true)

        log(VpnLogEntry.LogLevel.INFO, TAG, "VPN Tunnel Interface successfully created (fd: ${pfd.fd})")
        updateState(VpnState.Connected(
            server = server,
            connectedAtMillis = connectionStartTime,
            virtualIp = server.tunnelIpv4
        ))

        // Start Tunnel Worker Loop with packet engine
        startTunnelWorker(pfd, server, settings)
        startTelemetryLoop(server)
    }

    private fun startTunnelWorker(pfd: ParcelFileDescriptor, server: VpnServer, settings: VpnSettings) {
        tunnelJob = serviceScope.launch(Dispatchers.IO) {
            val inStream = FileInputStream(pfd.fileDescriptor)
            val outStream = FileOutputStream(pfd.fileDescriptor)

            val engine = VpnPacketEngine(
                protectSocket = { socket -> protect(socket) },
                upstreamDnsIps = settings.activeDnsList
            )
            engine.initialize()
            packetEngine = engine

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                val isProtected = protect(socket)
                log(VpnLogEntry.LogLevel.INFO, TAG, "Socket protected from VPN routing: $isProtected")

                val serverAddress = try {
                    InetAddress.getByName(server.host)
                } catch (e: Exception) {
                    InetAddress.getByName("127.0.0.1")
                }
                
                try {
                    socket.connect(InetSocketAddress(serverAddress, server.port))
                } catch (e: Exception) {
                    Log.w(TAG, "Socket connect failed (offline/simulated server)", e)
                }
                tunnelSocket = socket

                log(VpnLogEntry.LogLevel.INFO, TAG, "Tunnel packet engine active. DNS relay & security ready.")

                // Coroutine for reading outbound packets from TUN interface
                val outboundJob = launch {
                    val buffer = ByteArray(32767)
                    while (isActive && isRunning.get()) {
                        try {
                            val length = inStream.read(buffer)
                            if (length > 0) {
                                totalUploadBytes.addAndGet(length.toLong())

                                // 1. Attempt user-space packet resolution (DNS lookup or ICMP Ping)
                                val response = engine.processOutboundPacket(buffer, length)
                                if (response != null) {
                                    totalDownloadBytes.addAndGet(response.size.toLong())
                                    outStream.write(response)
                                } else {
                                    // 2. If connected to custom VPS server endpoint, forward packet
                                    if (server.isCustom || settings.routingMode == VpnSettings.RoutingMode.FULL_TUNNEL) {
                                        try {
                                            val datagram = DatagramPacket(buffer, length, serverAddress, server.port)
                                            socket.send(datagram)
                                        } catch (e: Exception) {
                                            // Handle silently
                                        }
                                    }
                                }
                            } else if (length < 0) {
                                break
                            }
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                Log.w(TAG, "Error reading from TUN stream: ${e.message}")
                            }
                            break
                        }
                    }
                }

                // Coroutine for reading inbound packets from socket and writing to TUN interface
                val inboundJob = launch {
                    val buffer = ByteArray(32767)
                    val incomingPacket = DatagramPacket(buffer, buffer.size)
                    while (isActive && isRunning.get()) {
                        try {
                            socket.receive(incomingPacket)
                            val length = incomingPacket.length
                            if (length > 0) {
                                totalDownloadBytes.addAndGet(length.toLong())
                                outStream.write(buffer, 0, length)
                            }
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                delay(200)
                            }
                        }
                    }
                }

                outboundJob.join()
                inboundJob.cancel()
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Tunnel worker exception", e)
                    log(VpnLogEntry.LogLevel.ERROR, TAG, "Tunnel error: ${e.message}")
                }
            } finally {
                engine.close()
                packetEngine = null
                socket?.close()
            }
        }
    }

    private fun startTelemetryLoop(server: VpnServer) {
        statsJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get()) {
                delay(1000)
                val currentUp = totalUploadBytes.get()
                val currentDown = totalDownloadBytes.get()

                val upSpeed = currentUp - lastUploadSnapshot
                val downSpeed = currentDown - lastDownloadSnapshot

                lastUploadSnapshot = currentUp
                lastDownloadSnapshot = currentDown

                updateState(VpnState.Connected(
                    server = server,
                    connectedAtMillis = connectionStartTime,
                    uploadBytes = currentUp,
                    downloadBytes = currentDown,
                    uploadSpeedBps = upSpeed,
                    downloadSpeedBps = downSpeed,
                    virtualIp = server.tunnelIpv4
                ))

                // Update Foreground Notification
                val durationSec = (System.currentTimeMillis() - connectionStartTime) / 1000
                val hours = durationSec / 3600
                val mins = (durationSec % 3600) / 60
                val secs = durationSec % 60
                val durationFormatted = if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, mins, secs)
                } else {
                    String.format("%02d:%02d", mins, secs)
                }

                val speedText = "↓ ${formatBytes(downSpeed)}/s • ↑ ${formatBytes(upSpeed)}/s"
                val notification = buildNotification(
                    title = "Connected: ${server.name} ($durationFormatted)",
                    content = speedText
                )
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopVpn(reason: String) {
        log(VpnLogEntry.LogLevel.INFO, TAG, "Stopping VPN: $reason")
        stopVpnInternal(reason)
        updateState(VpnState.Disconnected(reason))
        stopSelf()
    }

    private fun stopVpnInternal(reason: String) {
        isRunning.set(false)
        tunnelJob?.cancel()
        statsJob?.cancel()
        tunnelJob = null
        statsJob = null

        try {
            tunnelSocket?.close()
            tunnelSocket = null
        } catch (_: Exception) {}

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
    }

    override fun onRevoke() {
        super.onRevoke()
        log(VpnLogEntry.LogLevel.WARN, TAG, "VPN permission revoked by system or user in settings")
        updateState(VpnState.Error(
            message = "VPN Permission Revoked",
            technicalDetails = "System revoked VPN permission. Please re-grant VPN authorization.",
            errorType = VpnErrorType.PERMISSION_DENIED
        ))
        stopVpnInternal("Permission revoked")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnInternal("Service destroyed")
        unregisterNetworkCallback()
        serviceScope.cancel()
        log(VpnLogEntry.LogLevel.INFO, TAG, "VpnService destroyed and cleaned up")
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    log(VpnLogEntry.LogLevel.INFO, TAG, "Underlying network connected")
                }

                override fun onLost(network: Network) {
                    log(VpnLogEntry.LogLevel.WARN, TAG, "Underlying network connection lost")
                    if (isRunning.get() && repository.settings.value.autoReconnect) {
                        activeServer?.let { s ->
                            updateState(VpnState.Connecting(s, "Network interrupted. Waiting to reconnect..."))
                        }
                    }
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays live VPN connection status and traffic stats"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingDisconnect = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(pendingOpenApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", pendingDisconnect)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateState(newState: VpnState) {
        _vpnStateFlow.value = newState
    }

    private fun log(level: VpnLogEntry.LogLevel, tag: String, message: String) {
        serviceScope.launch {
            repository.log(level, tag, message)
        }
    }

    companion object {
        const val TAG = "MyVpnService"
        const val CHANNEL_ID = "vpn_channel_notifications"
        const val NOTIFICATION_ID = 1001

        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"

        const val EXTRA_SERVER_ID = "extra_server_id"
        const val EXTRA_SERVER_HOST = "extra_server_host"
        const val EXTRA_SERVER_PORT = "extra_server_port"
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_COUNTRY_CODE = "extra_country_code"
        const val EXTRA_COUNTRY_NAME = "extra_country_name"
        const val EXTRA_FLAG_EMOJI = "extra_flag_emoji"
        const val EXTRA_TUNNEL_IP = "extra_tunnel_ip"
        const val EXTRA_DNS = "extra_dns"
        const val EXTRA_MTU = "extra_mtu"
        const val EXTRA_IS_CUSTOM = "extra_is_custom"

        private val _vpnStateFlow = MutableStateFlow<VpnState>(VpnState.Disconnected())
        val vpnStateFlow: StateFlow<VpnState> = _vpnStateFlow.asStateFlow()

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
            return String.format("%.1f %sB", bytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
        }

        fun startService(context: Context, server: VpnServer) {
            val intent = Intent(context, MyVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_SERVER_ID, server.id)
                putExtra(EXTRA_SERVER_HOST, server.host)
                putExtra(EXTRA_SERVER_PORT, server.port)
                putExtra(EXTRA_SERVER_NAME, server.name)
                putExtra(EXTRA_COUNTRY_CODE, server.countryCode)
                putExtra(EXTRA_COUNTRY_NAME, server.countryName)
                putExtra(EXTRA_FLAG_EMOJI, server.flagEmoji)
                putExtra(EXTRA_TUNNEL_IP, server.tunnelIpv4)
                putExtra(EXTRA_MTU, server.mtu)
                putStringArrayListExtra(EXTRA_DNS, ArrayList(server.dnsServers))
                putExtra(EXTRA_IS_CUSTOM, server.isCustom)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MyVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }
}
