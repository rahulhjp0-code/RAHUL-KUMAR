package com.example.vpn.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vpn.model.VpnServer
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomServerDialog(
    initialServer: VpnServer? = null,
    onDismiss: () -> Unit,
    onSave: (VpnServer) -> Unit
) {
    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var host by remember { mutableStateOf(initialServer?.host ?: "") }
    var port by remember { mutableStateOf((initialServer?.port ?: 1194).toString()) }
    var protocol by remember { mutableStateOf(initialServer?.protocol ?: VpnServer.Protocol.UDP) }
    var tunnelIp by remember { mutableStateOf(initialServer?.tunnelIpv4 ?: "10.8.0.2") }
    var dnsPrimary by remember { mutableStateOf(initialServer?.dnsServers?.getOrNull(0) ?: "1.1.1.1") }
    var dnsSecondary by remember { mutableStateOf(initialServer?.dnsServers?.getOrNull(1) ?: "8.8.8.8") }
    var mtu by remember { mutableStateOf((initialServer?.mtu ?: 1420).toString()) }
    var psk by remember { mutableStateOf(initialServer?.preSharedKey ?: "") }

    var isProtocolDropdownExpanded by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialServer == null) "Add Custom VPN Server" else "Edit Server Profile",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Server Display Name") },
                    placeholder = { Text("e.g. My Ubuntu Cloud VPS") },
                    isError = nameError,
                    leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_server_name_input")
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it; hostError = false },
                        label = { Text("Host / IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        isError = hostError,
                        leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(2f)
                            .testTag("custom_server_host_input")
                    )

                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { char -> char.isDigit() } },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Protocol Dropdown
                ExposedDropdownMenuBox(
                    expanded = isProtocolDropdownExpanded,
                    onExpandedChange = { isProtocolDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = protocol.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isProtocolDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = isProtocolDropdownExpanded,
                        onDismissRequest = { isProtocolDropdownExpanded = false }
                    ) {
                        VpnServer.Protocol.entries.forEach { proto ->
                            DropdownMenuItem(
                                text = { Text(proto.displayName) },
                                onClick = {
                                    protocol = proto
                                    isProtocolDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tunnelIp,
                    onValueChange = { tunnelIp = it },
                    label = { Text("Virtual Tunnel IP") },
                    placeholder = { Text("10.8.0.2") },
                    leadingIcon = { Icon(Icons.Filled.Lan, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = dnsPrimary,
                        onValueChange = { dnsPrimary = it },
                        label = { Text("Primary DNS") },
                        placeholder = { Text("1.1.1.1") },
                        leadingIcon = { Icon(Icons.Filled.Dns, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = dnsSecondary,
                        onValueChange = { dnsSecondary = it },
                        label = { Text("Secondary DNS") },
                        placeholder = { Text("8.8.8.8") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = mtu,
                        onValueChange = { mtu = it.filter { char -> char.isDigit() } },
                        label = { Text("MTU Size") },
                        placeholder = { Text("1420") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Filled.Speed, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = psk,
                        onValueChange = { psk = it },
                        label = { Text("Pre-Shared Key (Opt)") },
                        placeholder = { Text("••••••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    if (host.isBlank()) {
                        hostError = true
                        return@Button
                    }
                    val parsedPort = port.toIntOrNull() ?: 1194
                    val parsedMtu = mtu.toIntOrNull() ?: 1420
                    val dnsList = listOfNotNull(
                        dnsPrimary.takeIf { it.isNotBlank() },
                        dnsSecondary.takeIf { it.isNotBlank() }
                    ).ifEmpty { listOf("1.1.1.1", "8.8.8.8") }

                    val saved = VpnServer(
                        id = initialServer?.id ?: "custom_${UUID.randomUUID().toString().take(8)}",
                        name = name.trim(),
                        countryCode = initialServer?.countryCode ?: "UN",
                        countryName = initialServer?.countryName ?: "Custom Node",
                        flagEmoji = initialServer?.flagEmoji ?: "🛡️",
                        host = host.trim(),
                        port = parsedPort,
                        protocol = protocol,
                        tunnelIpv4 = tunnelIp.trim().ifBlank { "10.8.0.2" },
                        tunnelPrefixLength = 24,
                        dnsServers = dnsList,
                        mtu = parsedMtu,
                        isCustom = true,
                        isFavorite = initialServer?.isFavorite ?: false,
                        preSharedKey = psk.takeIf { it.isNotBlank() }
                    )
                    onSave(saved)
                },
                modifier = Modifier.testTag("save_custom_server_button")
            ) {
                Text("Save Server")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
