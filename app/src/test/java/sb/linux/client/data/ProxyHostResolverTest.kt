package sb.linux.client.data

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 只测 orderAddresses 的纯逻辑：IPv6 在前，同族内保持 DNS 原顺序。
 * 全部用 IP 字面量构造地址（getByName 对字面量不查 DNS），不依赖真网。
 */
class ProxyHostResolverTest {
    private fun v4(address: String): InetAddress = InetAddress.getByName(address)
    private fun v6(address: String): InetAddress = InetAddress.getByName(address)

    @Test
    fun ipv6ComesFirst() {
        val v4 = v4("127.0.0.1")
        val v6 = v6("::1")
        assertEquals(listOf(v6, v4), ProxyHostResolver.orderAddresses(listOf(v4, v6)))
    }

    @Test
    fun sameFamilyOrderPreserved() {
        val v4a = v4("127.0.0.1")
        val v4b = v4("192.0.2.1")
        val v6a = v6("2001:db8::1")
        val v6b = v6("::1")
        assertEquals(
            listOf(v6b, v6a, v4a, v4b),
            ProxyHostResolver.orderAddresses(listOf(v6b, v4a, v6a, v4b)),
        )
    }

    @Test
    fun emptyAndSingleUnchanged() {
        assertEquals(emptyList<InetAddress>(), ProxyHostResolver.orderAddresses(emptyList()))
        val single = v4("127.0.0.1")
        assertEquals(listOf(single), ProxyHostResolver.orderAddresses(listOf(single)))
    }

    @Test
    fun allIpv4KeepsOrder() {
        val a = v4("127.0.0.1")
        val b = v4("192.0.2.1")
        assertEquals(listOf(a, b), ProxyHostResolver.orderAddresses(listOf(a, b)))
    }
}
