package com.example.vpn.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.vpn.model.VpnServer

@Entity(tableName = "vpn_servers")
data class VpnServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val flagEmoji: String,
    val host: String,
    val port: Int,
    val protocol: String,
    val tunnelIpv4: String,
    val tunnelPrefixLength: Int,
    val dnsServersCsv: String,
    val mtu: Int,
    val isCustom: Boolean,
    val isFavorite: Boolean,
    val pingMs: Int,
    val loadPercent: Int,
    val isAvailable: Boolean,
    val preSharedKey: String?
) {
    fun toDomain(): VpnServer {
        val proto = try {
            VpnServer.Protocol.valueOf(protocol)
        } catch (_: Exception) {
            VpnServer.Protocol.UDP
        }
        val dnsList = if (dnsServersCsv.isBlank()) {
            listOf("1.1.1.1", "8.8.8.8")
        } else {
            dnsServersCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return VpnServer(
            id = id,
            name = name,
            countryCode = countryCode,
            countryName = countryName,
            flagEmoji = flagEmoji,
            host = host,
            port = port,
            protocol = proto,
            tunnelIpv4 = tunnelIpv4,
            tunnelPrefixLength = tunnelPrefixLength,
            dnsServers = dnsList,
            mtu = mtu,
            isCustom = isCustom,
            isFavorite = isFavorite,
            pingMs = pingMs,
            loadPercent = loadPercent,
            isAvailable = isAvailable,
            preSharedKey = preSharedKey
        )
    }

    companion object {
        fun fromDomain(server: VpnServer): VpnServerEntity {
            return VpnServerEntity(
                id = server.id,
                name = server.name,
                countryCode = server.countryCode,
                countryName = server.countryName,
                flagEmoji = server.flagEmoji,
                host = server.host,
                port = server.port,
                protocol = server.protocol.name,
                tunnelIpv4 = server.tunnelIpv4,
                tunnelPrefixLength = server.tunnelPrefixLength,
                dnsServersCsv = server.dnsServers.joinToString(","),
                mtu = server.mtu,
                isCustom = server.isCustom,
                isFavorite = server.isFavorite,
                pingMs = server.pingMs,
                loadPercent = server.loadPercent,
                isAvailable = server.isAvailable,
                preSharedKey = server.preSharedKey
            )
        }
    }
}
