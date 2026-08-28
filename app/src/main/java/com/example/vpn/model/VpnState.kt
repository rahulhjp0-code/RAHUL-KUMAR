package com.example.vpn.model

/**
 * Represents the current lifecycle and connection state of the VPN.
 */
sealed interface VpnState {
    /**
     * VPN is inactive and no tunnel is open.
     */
    data class Disconnected(
        val reason: String? = null
    ) : VpnState

    /**
     * VPN is negotiating connection / preparing tunnel.
     */
    data class Connecting(
        val server: VpnServer,
        val stage: String = "Initializing handshake..."
    ) : VpnState

    /**
     * VPN tunnel is established and actively routing packets.
     */
    data class Connected(
        val server: VpnServer,
        val connectedAtMillis: Long = System.currentTimeMillis(),
        val uploadBytes: Long = 0L,
        val downloadBytes: Long = 0L,
        val uploadSpeedBps: Long = 0L,
        val downloadSpeedBps: Long = 0L,
        val virtualIp: String = "10.8.0.2",
        val isBackendReachable: Boolean = true
    ) : VpnState

    /**
     * VPN encountered an error during startup, connection, or network transition.
     */
    data class Error(
        val message: String,
        val technicalDetails: String? = null,
        val errorType: VpnErrorType = VpnErrorType.GENERIC,
        val timestamp: Long = System.currentTimeMillis()
    ) : VpnState
}

enum class VpnErrorType {
    PERMISSION_DENIED,
    NETWORK_UNAVAILABLE,
    TUNNEL_CREATION_FAILED,
    SERVER_UNREACHABLE,
    AUTHENTICATION_FAILED,
    GENERIC
}
