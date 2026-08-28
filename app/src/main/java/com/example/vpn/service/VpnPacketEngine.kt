package com.example.vpn.service

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * High-performance user-space IPv4 packet engine for VpnService.
 * Intercepts DNS queries (UDP 53) and ICMP Echo requests, resolves them
 * using protected upstream DNS sockets, and crafts authentic IPv4 response packets.
 */
class VpnPacketEngine(
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val upstreamDnsIps: List<String>
) {
    companion object {
        private const val TAG = "VpnPacketEngine"
    }

    private var dnsSocket: DatagramSocket? = null

    fun initialize() {
        try {
            val socket = DatagramSocket()
            val protected = protectSocket(socket)
            socket.soTimeout = 3500
            dnsSocket = socket
            Log.d(TAG, "DNS Relay Socket initialized and protected ($protected)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DNS socket", e)
        }
    }

    fun close() {
        try {
            dnsSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        dnsSocket = null
    }

    /**
     * Inspects and processes an IPv4 packet read from the TUN descriptor.
     * Returns a crafted IPv4 response packet (e.g. DNS response or ICMP reply)
     * to be written back to the TUN descriptor, or null if unhandled.
     */
    fun processOutboundPacket(packet: ByteArray, length: Int): ByteArray? {
        if (length < 20) return null
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (length < ihl) return null

        val protocol = packet[9].toInt() and 0xFF

        // 1. ICMP Ping (Protocol 1) -> Synthesize ICMP Echo Reply
        if (protocol == 1 && length >= ihl + 8) {
            val icmpType = packet[ihl].toInt() and 0xFF
            if (icmpType == 8) { // Echo Request
                return handleIcmpEchoRequest(packet, length, ihl)
            }
        }

        // 2. UDP (Protocol 17) -> Check for DNS (Destination Port 53)
        if (protocol == 17 && length >= ihl + 8) {
            val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
            val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

            if (dstPort == 53) {
                val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
                val payloadOffset = ihl + 8
                val payloadLength = minOf(udpLength - 8, length - payloadOffset)
                if (payloadLength > 0) {
                    val dnsPayload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLength)
                    return resolveDnsAndBuildResponse(
                        packet = packet,
                        ihl = ihl,
                        srcPort = srcPort,
                        dstPort = dstPort,
                        dnsPayload = dnsPayload
                    )
                }
            }
        }

        return null
    }

    private fun handleIcmpEchoRequest(packet: ByteArray, length: Int, ihl: Int): ByteArray {
        val response = packet.copyOf(length)
        // Swap IPv4 Source and Destination IP
        for (i in 0..3) {
            val temp = response[12 + i]
            response[12 + i] = response[16 + i]
            response[16 + i] = temp
        }
        // Change ICMP Type from 8 (Echo Request) to 0 (Echo Reply)
        response[ihl] = 0.toByte()

        // Reset ICMP Checksum and compute
        response[ihl + 2] = 0
        response[ihl + 3] = 0
        val icmpLen = length - ihl
        val icmpChecksum = computeChecksum(response, ihl, icmpLen)
        response[ihl + 2] = ((icmpChecksum shr 8) and 0xFF).toByte()
        response[ihl + 3] = (icmpChecksum and 0xFF).toByte()

        // Recalculate IPv4 Checksum
        recalculateIpChecksum(response, ihl)
        return response
    }

    private fun resolveDnsAndBuildResponse(
        packet: ByteArray,
        ihl: Int,
        srcPort: Int,
        dstPort: Int,
        dnsPayload: ByteArray
    ): ByteArray? {
        val socket = dnsSocket ?: return null
        val targetDns = upstreamDnsIps.firstOrNull { it.isNotBlank() } ?: "1.1.1.1"

        try {
            val dnsServerAddr = InetAddress.getByName(targetDns)
            val sendPacket = DatagramPacket(dnsPayload, dnsPayload.size, dnsServerAddr, 53)
            socket.send(sendPacket)

            val recvBuffer = ByteArray(2048)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            socket.receive(recvPacket)

            val dnsResponseBytes = recvBuffer.copyOf(recvPacket.length)

            // Construct full IPv4 + UDP packet
            val totalIpLen = ihl + 8 + dnsResponseBytes.size
            val responsePacket = ByteArray(totalIpLen)

            // 1. IPv4 Header
            System.arraycopy(packet, 0, responsePacket, 0, ihl)
            responsePacket[2] = ((totalIpLen shr 8) and 0xFF).toByte()
            responsePacket[3] = (totalIpLen and 0xFF).toByte()
            responsePacket[8] = 64.toByte() // TTL
            responsePacket[9] = 17.toByte() // Protocol = 17 (UDP)

            // Swap Source & Destination IP
            System.arraycopy(packet, 16, responsePacket, 12, 4) // Dst -> Src
            System.arraycopy(packet, 12, responsePacket, 16, 4) // Src -> Dst

            // Compute IPv4 header checksum
            recalculateIpChecksum(responsePacket, ihl)

            // 2. UDP Header
            // Source Port = 53
            responsePacket[ihl] = ((dstPort shr 8) and 0xFF).toByte()
            responsePacket[ihl + 1] = (dstPort and 0xFF).toByte()
            // Destination Port = original source port
            responsePacket[ihl + 2] = ((srcPort shr 8) and 0xFF).toByte()
            responsePacket[ihl + 3] = (srcPort and 0xFF).toByte()

            val udpLength = 8 + dnsResponseBytes.size
            responsePacket[ihl + 4] = ((udpLength shr 8) and 0xFF).toByte()
            responsePacket[ihl + 5] = (udpLength and 0xFF).toByte()
            responsePacket[ihl + 6] = 0 // UDP checksum (0 means uncomputed in IPv4)
            responsePacket[ihl + 7] = 0

            // 3. DNS Response Payload
            System.arraycopy(dnsResponseBytes, 0, responsePacket, ihl + 8, dnsResponseBytes.size)

            return responsePacket
        } catch (e: Exception) {
            Log.w(TAG, "DNS query forward failed: ${e.message}")
            return null
        }
    }

    private fun recalculateIpChecksum(packet: ByteArray, ihl: Int) {
        packet[10] = 0
        packet[11] = 0
        val checksum = computeChecksum(packet, 0, ihl)
        packet[10] = ((checksum shr 8) and 0xFF).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            val word = (high shl 8) or low
            sum += word
            i += 2
        }
        if (i < offset + length) {
            val high = data[i].toInt() and 0xFF
            sum += (high shl 8)
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }
}
