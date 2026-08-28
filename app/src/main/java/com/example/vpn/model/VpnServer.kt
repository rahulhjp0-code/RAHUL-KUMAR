package com.example.vpn.model

/**
 * Represents a VPN Server profile with connection parameters and telemetry.
 */
data class VpnServer(
    val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val flagEmoji: String,
    val host: String,
    val port: Int = 1194,
    val protocol: Protocol = Protocol.UDP,
    val tunnelIpv4: String = "10.8.0.2",
    val tunnelPrefixLength: Int = 24,
    val dnsServers: List<String> = listOf("1.1.1.1", "8.8.8.8"),
    val mtu: Int = 1500,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val pingMs: Int = 0,
    val loadPercent: Int = 0,
    val isAvailable: Boolean = true,
    val preSharedKey: String? = null
) {
    enum class Protocol(val displayName: String) {
        UDP("UDP (Fast)"),
        TCP("TCP (Reliable)"),
        WIREGUARD_UDP("WireGuard UDP")
    }

    val displayAddress: String
        get() = "$host:$port"
}
