package sb.linux.client.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import sb.linux.client.domain.network.ProxyMode
import sb.linux.client.domain.network.ProxyProfile
import sb.linux.client.domain.network.ProxyProfileRepository
import sb.linux.client.domain.network.ProxyScope
import sb.linux.client.domain.network.ProxyType

/**
 * 多代理配置存同一 SharedPreferences（lsb_app_settings），JSONArray 字符串。
 * 旧 proxy_type/host/port 三键保留作镜像（见 [sb.linux.client.data.ProxyRouter]），
 * 密码按项目现有惯例放 SharedPreferences 明文——见 ProxyProfilesScreen 的提示，
 * 不要把备份文件发给别人。
 */
internal class ProxyProfileRepositoryImpl(context: Context) : ProxyProfileRepository {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lsb_app_settings", Context.MODE_PRIVATE)

    override fun profiles(): List<ProxyProfile> {
        ensureSeeded()
        val loaded = readProfiles()
        // 1.2.4 迁移：实测 57081 只握手不通流量、57080 才通；已保存的配置里只有
        // host/port 与旧示例值完全相同的那条才改端口，用户自己改过的端口绝不动。
        if (loaded.none { it.host == LAN_EXAMPLE_HOST && it.port == LAN_LEGACY_PORT }) return loaded
        val migrated = loaded.map {
            if (it.host == LAN_EXAMPLE_HOST && it.port == LAN_LEGACY_PORT) it.copy(port = LAN_EXAMPLE_PORT) else it
        }
        saveAll(migrated)
        return migrated
    }

    override fun saveAll(profiles: List<ProxyProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put("id", profile.id)
                    .put("name", profile.name)
                    .put("type", profile.type.name)
                    .put("host", profile.host.trim())
                    .put("port", profile.port)
                    .put("username", profile.username)
                    .put("password", profile.password)
                    .put("scope", profile.scope.name)
                    .put("priority", profile.priority)
                    .put("enabled", profile.enabled),
            )
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    override fun mode(): ProxyMode = runCatching {
        ProxyMode.valueOf(prefs.getString(KEY_MODE, ProxyMode.AUTO.name) ?: ProxyMode.AUTO.name)
    }.getOrDefault(ProxyMode.AUTO)

    override fun setMode(mode: ProxyMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    override fun manualProfileId(): String? =
        prefs.getString(KEY_MANUAL_ID, null)?.takeIf { it.isNotBlank() }

    override fun setManualProfileId(id: String?) {
        prefs.edit().putString(KEY_MANUAL_ID, id.orEmpty()).apply()
    }

    override fun failoverDirect(): Boolean = prefs.getBoolean(KEY_FAILOVER_DIRECT, false)

    override fun setFailoverDirect(value: Boolean) {
        prefs.edit().putBoolean(KEY_FAILOVER_DIRECT, value).apply()
    }

    override fun ensureSeeded() {
        if (prefs.contains(KEY_PROFILES)) return
        // 旧单代理还在：迁移成一条 scope=ALL 的 profile，不丢用户已有配置。
        val legacyType = prefs.getInt("proxy_type", 0)
        val legacyHost = prefs.getString("proxy_host", "").orEmpty().trim()
        val legacyPort = prefs.getInt("proxy_port", 0)
        if (legacyType in 1..2 && legacyHost.isNotBlank() && legacyPort in 1..65535) {
            saveAll(
                listOf(
                    ProxyProfile(
                        name = "迁移的代理",
                        type = if (legacyType == 2) ProxyType.SOCKS5 else ProxyType.HTTP,
                        host = legacyHost,
                        port = legacyPort,
                        scope = ProxyScope.ALL,
                        priority = 0,
                    ),
                ),
            )
            return
        }
        // 全新：预置两条占位 profile。
        // 内网那条给的是可改的示例值 192.168.124.250:57080，不是写死——
        // 使用者在代理列表里随时能改 host/port；外网那条 host 留空，等使用者自己填域名。
        saveAll(
            listOf(
                ProxyProfile(
                    name = "家里 SOCKS5",
                    type = ProxyType.SOCKS5,
                    host = LAN_EXAMPLE_HOST,
                    port = LAN_EXAMPLE_PORT,
                    scope = ProxyScope.LAN_ONLY,
                    priority = 0,
                ),
                ProxyProfile(
                    name = "公网域名代理",
                    type = ProxyType.HTTP,
                    host = "",
                    port = 0,
                    scope = ProxyScope.WAN_ONLY,
                    priority = 0,
                ),
            ),
        )
    }

    override fun reset() {
        prefs.edit()
            .remove(KEY_PROFILES)
            .remove(KEY_MODE)
            .remove(KEY_MANUAL_ID)
            .remove(KEY_FAILOVER_DIRECT)
            .remove("proxy_type")
            .remove("proxy_host")
            .remove("proxy_port")
            .remove("proxy_auto_detected")
            .apply()
    }

    private fun readProfiles(): List<ProxyProfile> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PROFILES, "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val o = array.getJSONObject(index)
                ProxyProfile(
                    id = o.optString("id").takeIf { it.isNotBlank() }
                        ?: java.util.UUID.randomUUID().toString(),
                    name = o.optString("name").ifBlank { "代理" },
                    type = runCatching { ProxyType.valueOf(o.optString("type")) }
                        .getOrDefault(ProxyType.HTTP),
                    host = o.optString("host"),
                    port = o.optInt("port", 0),
                    username = o.optString("username"),
                    password = o.optString("password"),
                    scope = runCatching { ProxyScope.valueOf(o.optString("scope")) }
                        .getOrDefault(ProxyScope.ALL),
                    priority = o.optInt("priority", 0),
                    enabled = o.optBoolean("enabled", true),
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    companion object {
        const val KEY_PROFILES = "proxy_profiles"
        const val KEY_MODE = "proxy_mode"
        const val KEY_MANUAL_ID = "proxy_active_profile_id"
        const val KEY_FAILOVER_DIRECT = "proxy_failover_direct"

        /** 内网示例值（可改，不是写死）：家里 SOCKS5 默认地址。 */
        const val LAN_EXAMPLE_HOST = "192.168.124.250"
        const val LAN_EXAMPLE_PORT = 57080
        /** 1.2.4 之前种子用的旧端口：实测只握手不通流量，已废弃，仅供迁移判断。 */
        const val LAN_LEGACY_PORT = 57081
    }
}
