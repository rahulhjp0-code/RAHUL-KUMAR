package com.example.vpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberConnecting
import com.example.ui.theme.CoralError
import com.example.ui.theme.Cyan80
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.EmeraldConnected
import com.example.vpn.model.VpnServer
import com.example.vpn.model.VpnState
import com.example.vpn.service.MyVpnService
import com.example.vpn.ui.components.BackendDisclaimerBanner
import com.example.vpn.ui.components.DurationDisplay
import com.example.vpn.ui.components.StatusBadge
import com.example.vpn.ui.components.TelemetryCard

@Composable
fun DashboardScreen(
    vpnState: VpnState,
    selectedServer: VpnServer?,
    durationSeconds: Long,
    onConnectToggle: () -> Unit,
    onNavigateToServers: () -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenBackendGuide: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val isConnected = vpnState is VpnState.Connected
    val isConnecting = vpnState is VpnState.Connecting
    val isError = vpnState is VpnState.Error

    val uploadBytes = if (vpnState is VpnState.Connected) vpnState.uploadBytes else 0L
    val downloadBytes = if (vpnState is VpnState.Connected) vpnState.downloadBytes else 0L
    val uploadSpeed = if (vpnState is VpnState.Connected) vpnState.uploadSpeedBps else 0L
    val downloadSpeed = if (vpnState is VpnState.Connected) vpnState.downloadSpeedBps else 0L
    val virtualIp = if (vpnState is VpnState.Connected) vpnState.virtualIp else (selectedServer?.tunnelIpv4 ?: "10.8.0.2")

    val glowColor = when (vpnState) {
        is VpnState.Connected -> EmeraldConnected
        is VpnState.Connecting -> AmberConnecting
        is VpnState.Error -> CoralError
        is VpnState.Disconnected -> Cyan80
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Status Badge at the top
        StatusBadge(vpnState = vpnState)

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Duration Timer
        AnimatedVisibility(
            visible = isConnected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            DurationDisplay(durationSeconds = durationSeconds)
        }

        if (!isConnected) {
            Text(
                text = if (isConnecting) "ESTABLISHING ENCRYPTED TUNNEL" else "TAP TO SECURE CONNECTION",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Large Circular Connect / Disconnect Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            // Outer glowing ring
            if (isConnected || isConnecting) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(if (isConnected) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(glowColor.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )
            }

            // Outer decorative ring border
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.sweepGradient(
                            listOf(
                                glowColor.copy(alpha = 0.8f),
                                glowColor.copy(alpha = 0.2f),
                                glowColor.copy(alpha = 0.8f)
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Inner Action Button
            Surface(
                shape = CircleShape,
                color = when {
                    isConnected -> EmeraldConnected
                    isConnecting -> AmberConnecting
                    isError -> CoralError
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shadowElevation = 8.dp,
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onConnectToggle)
                    .testTag("vpn_power_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier
                                .size(70.dp)
                                .rotate(spinAngle)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isConnecting -> Icons.Filled.Sync
                                isConnected -> Icons.Filled.PowerSettingsNew
                                isError -> Icons.Filled.Error
                                else -> Icons.Filled.PowerSettingsNew
                            },
                            contentDescription = "VPN Toggle",
                            tint = if (isConnected || isConnecting || isError) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .rotate(if (isConnecting) spinAngle else 0f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when {
                                isConnected -> "DISCONNECT"
                                isConnecting -> "CANCEL"
                                isError -> "RETRY"
                                else -> "CONNECT"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = if (isConnected || isConnecting || isError) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Error message banner if state is Error
        if (vpnState is VpnState.Error) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CoralError.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, CoralError.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = CoralError,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vpnState.message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralError
                        )
                        if (vpnState.technicalDetails != null) {
                            Text(
                                text = vpnState.technicalDetails,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismissError) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = CoralError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Active Selected Server Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToServers)
                .testTag("selected_server_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = selectedServer?.flagEmoji ?: "🛡️",
                    fontSize = 28.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SELECTED SERVER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = selectedServer?.name ?: "Select a Server",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${selectedServer?.host ?: "127.0.0.1"}:${selectedServer?.port ?: 1194} • ${selectedServer?.protocol?.displayName ?: "UDP"}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = "Change Server",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live Telemetry Grid (Download, Upload, Virtual IP, Protocol)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TelemetryCard(
                title = "DOWNLOAD SPEED",
                value = "${MyVpnService.formatBytes(downloadSpeed)}/s",
                subValue = "Total: ${MyVpnService.formatBytes(downloadBytes)}",
                icon = Icons.Filled.ArrowDownward,
                accentColor = EmeraldConnected,
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                title = "UPLOAD SPEED",
                value = "${MyVpnService.formatBytes(uploadSpeed)}/s",
                subValue = "Total: ${MyVpnService.formatBytes(uploadBytes)}",
                icon = Icons.Filled.ArrowUpward,
                accentColor = Cyan80,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TelemetryCard(
                title = "VIRTUAL IP",
                value = virtualIp,
                subValue = "Subnet /24",
                icon = Icons.Filled.Lan,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                title = "TUNNEL MTU",
                value = "${selectedServer?.mtu ?: 1420} B",
                subValue = "Protocol: ${selectedServer?.protocol?.name ?: "UDP"}",
                icon = Icons.Filled.Speed,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Web Browser & Live Site Tester Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBrowser)
                .testTag("dashboard_web_tester_card")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "WEB BROWSER & SITE TESTER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        if (isConnected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = EmeraldConnected.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "PROTECTED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldConnected,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Test Sites & Live Internet Access",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Open any site, check DNS security & latency ms",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Backend Server Setup Guide & Disclaimer
        BackendDisclaimerBanner(onOpenGuide = onOpenBackendGuide)

        Spacer(modifier = Modifier.height(24.dp))
    }
}
