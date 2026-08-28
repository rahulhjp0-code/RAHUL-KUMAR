package com.example.vpn.model

/**
 * Diagnostic log entry for VPN operations.
 */
data class VpnLogEntry(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "VPN",
    val message: String
) {
    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        TRAFFIC
    }
}
