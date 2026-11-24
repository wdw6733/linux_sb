package sb.linux.client.data

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sb.linux.client.domain.network.ProxyEnvironment
import sb.linux.client.domain.network.ProxyMode
import sb.linux.client.domain.network.ProxyProfile
import sb.linux.client.domain.network.ProxyScope
import sb.linux.client.domain.network.ProxyType

class ProxyRouterTest {
    private val lan = ProxyEnvironment(true, "lan")
    private val wan = ProxyEnvironment(false, "wan")

    private fun profile(
        name: String,
        scope: ProxyScope,
        priority: Int = 0,
        enabled: Boolean = true,
        host: String = "10.0.0.1",
        port: Int = 8080,
    ) = ProxyProfile(
        name = name,
        type = ProxyType.HTTP,
        host = host,
        port = port,
        scope = scope,
        priority = priority,
        enabled = enabled,
    )

    @Test
    fun lanEnvironmentPrefersLanOnlyOverAll() {
        val profiles = listOf(
            profile("all", ProxyScope.ALL, priority = 0),
            profile("lan", ProxyScope.LAN_ONLY, priority = 1),
        )
        // 优先级小的先用：ALL priority 0 在内网也可用，排第一。
        assertEquals("all", ProxyRouter.resolve(profiles, lan, ProxyMode.AUTO, null)?.name)
        val lanOnlyFirst = listOf(
            profile("lan", ProxyScope.LAN_ONLY, priority = 0),
            profile("all", ProxyScope.ALL, priority = 1),
        )
        assertEquals("lan", ProxyRouter.resolve(lanOnlyFirst, lan, ProxyMode.AUTO, null)?.name)
    }

    @Test
    fun wanEnvironmentSkipsLanOnly() {
        val profiles = listOf(
            profile("lan", ProxyScope.LAN_ONLY, priority = 0),
            profile("wan", ProxyScope.WAN_ONLY, priority = 1),
        )
        assertEquals("wan", ProxyRouter.resolve(profiles, wan, ProxyMode.AUTO, null)?.name)
    }

    @Test
    fun unconfiguredPlaceholderIsSkipped() {
        val profiles = listOf(
            // 外网占位：host 为空，等用户填。
            ProxyProfile(name = "公网域名代理", type = ProxyType.HTTP, host = "", port = 0, scope = ProxyScope.WAN_ONLY),
            profile("all", ProxyScope.ALL, priority = 1),
        )
        assertEquals("all", ProxyRouter.resolve(profiles, wan, ProxyMode.AUTO, null)?.name)
        // 只有占位时返回 null，不拿空 host 去连。
        assertNull(ProxyRouter.resolve(profiles.take(1), wan, ProxyMode.AUTO, null))
    }

    @Test
    fun disabledProfileIsSkipped() {
        val profiles = listOf(
            profile("off", ProxyScope.ALL, priority = 0, enabled = false),
            profile("on", ProxyScope.ALL, priority = 1),
        )
        assertEquals("on", ProxyRouter.resolve(profiles, lan, ProxyMode.AUTO, null)?.name)
    }

    @Test
    fun offModeResolvesNothing() {
        val profiles = listOf(profile("lan", ProxyScope.LAN_ONLY))
        assertNull(ProxyRouter.resolve(profiles, lan, ProxyMode.OFF, null))
    }

    @Test
    fun manualModeLocksSpecifiedProfile() {
        val locked = profile("locked", ProxyScope.WAN_ONLY)
        val profiles = listOf(profile("lan", ProxyScope.LAN_ONLY, priority = 0), locked)
        assertEquals("locked", ProxyRouter.resolve(profiles, lan, ProxyMode.MANUAL, locked.id)?.name)
        // 锁定的 id 不存在时返回 null，不回退到自动。
        assertNull(ProxyRouter.resolve(profiles, lan, ProxyMode.MANUAL, "missing-id"))
    }

    @Test
    fun candidatesArePriorityOrdered() {
        val profiles = listOf(
            profile("b", ProxyScope.ALL, priority = 5),
            profile("a", ProxyScope.ALL, priority = 1),
            profile("c", ProxyScope.ALL, priority = 9),
        )
        assertEquals(
            listOf("a", "b", "c"),
            ProxyRouter.candidates(profiles, lan, ProxyMode.AUTO, null).map { it.name },
        )
    }

    @Test
    fun repeatedFailuresExcludeCandidate() {
        val flaky = profile("flaky", ProxyScope.ALL, priority = 0)
        val backup = profile("backup", ProxyScope.ALL, priority = 1)
        val profiles = listOf(flaky, backup)
        repeat(3) { ProxyRouter.recordFailure(flaky.id) }
        try {
            assertEquals(
                listOf("backup"),
                ProxyRouter.usableCandidates(profiles, lan, ProxyMode.AUTO, null).map { it.name },
            )
        } finally {
            ProxyRouter.clearFailures()
        }
    }
}

class ProxyEnvironmentDetectorTest {
    @Test
    fun lanAddressBoundary() {
        assertTrue(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("192.168.0.1")))
        assertTrue(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("192.168.124.250")))
        assertTrue(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("192.168.255.255")))
        assertFalse(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("192.169.0.1")))
        assertFalse(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("192.167.255.255")))
        assertFalse(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("10.0.0.1")))
        assertFalse(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("172.16.0.1")))
        assertFalse(ProxyEnvironmentDetector.isLanAddress(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun classifyRequiresLocalTransportAndLanProbe() {
        assertTrue(ProxyEnvironmentDetector.classify(true, true).isLan)
        assertFalse(ProxyEnvironmentDetector.classify(false, true).isLan)
        assertFalse(ProxyEnvironmentDetector.classify(true, false).isLan)
        assertFalse(ProxyEnvironmentDetector.classify(false, false).isLan)
    }
}
