package com.example.vpn.model

/**
 * User-configurable settings for VPN security and behavior.
 */
data class VpnSettings(
    val killSwitchEnabled: Boolean = false,
    val autoReconnect: Boolean = true,
    val splitTunnelMode: SplitTunnelMode = SplitTunnelMode.DISABLED,
    val excludedAppPackages: Set<String> = emptySet(),
    val includedAppPackages: Set<String> = emptySet(),
    val defaultDns: DnsOption = DnsOption.CLOUDFLARE,
    val customDnsPrimary: String = "1.1.1.1",
    val customDnsSecondary: String = "1.0.0.1",
    val mtu: Int = 1500,
    val protocol: VpnServer.Protocol = VpnServer.Protocol.UDP,
    val autoConnectOnWifi: Boolean = false,
    val routingMode: RoutingMode = RoutingMode.SMART_DNS_SHIELD,
    val bypassLan: Boolean = true
) {
    enum class RoutingMode(val title: String, val description: String) {
        SMART_DNS_SHIELD(
            "Smart Shield & Secure DNS (Recommended)",
            "Encrypts all DNS requests, blocks tracking/malware, and guarantees blazing-fast uninterrupted browsing for all sites."
        ),
        FULL_TUNNEL(
            "Full IPv4 Tunnel (0.0.0.0/0)",
            "Routes all device IPv4 traffic through the VPN virtual interface with user-space DNS resolver."
        )
    }

    enum class SplitTunnelMode(val title: String, val description: String) {
        DISABLED("Disabled", "All application traffic routes through the VPN tunnel"),
        EXCLUDE_SELECTED("Bypass Selected Apps", "Selected apps bypass VPN and use direct internet"),
        INCLUDE_ONLY_SELECTED("Only Route Selected Apps", "Only chosen apps route through VPN")
    }

    enum class DnsOption(val title: String, val primary: String, val secondary: String) {
        CLOUDFLARE("Cloudflare (1.1.1.1)", "1.1.1.1", "1.0.0.1"),
        GOOGLE("Google Public DNS", "8.8.8.8", "8.8.4.4"),
        QUAD9("Quad9 (Malware Blocking)", "9.9.9.9", "149.112.112.112"),
        ADGUARD("AdGuard DNS (Ad Blocking)", "94.140.14.14", "94.140.15.15"),
        CUSTOM("Custom DNS Server", "", "")
    }

    val activeDnsList: List<String>
        get() = when (defaultDns) {
            DnsOption.CUSTOM -> listOfNotNull(
                customDnsPrimary.takeIf { it.isNotBlank() },
                customDnsSecondary.takeIf { it.isNotBlank() }
            ).ifEmpty { listOf("1.1.1.1") }
            else -> listOf(defaultDns.primary, defaultDns.secondary)
        }
}

data class AppInfo(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false
)
