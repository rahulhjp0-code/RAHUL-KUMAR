package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.vpn.model.VpnSettings
import com.example.vpn.service.VpnPacketEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Secure VPN", appName)
  }

  @Test
  fun `verify vpn settings default routing mode is Smart Shield`() {
    val settings = VpnSettings()
    assertEquals(VpnSettings.RoutingMode.SMART_DNS_SHIELD, settings.routingMode)
    assertEquals(true, settings.bypassLan)
  }

  @Test
  fun `verify vpn packet engine initialization`() {
    val engine = VpnPacketEngine(
      protectSocket = { true },
      upstreamDnsIps = listOf("1.1.1.1", "8.8.8.8")
    )
    engine.initialize()
    // Process invalid packet length should return null
    val result = engine.processOutboundPacket(ByteArray(10), 10)
    assertEquals(null, result)
    engine.close()
  }
}
