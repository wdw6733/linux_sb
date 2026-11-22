package sb.linux.client.domain.network

/** 多代理配置的持久化契约：profile 列表 + 模式 + 手动锁定 + 直连回退。 */
interface ProxyProfileRepository {
    fun profiles(): List<ProxyProfile>
    fun saveAll(profiles: List<ProxyProfile>)
    fun mode(): ProxyMode
    fun setMode(mode: ProxyMode)
    fun manualProfileId(): String?
    fun setManualProfileId(id: String?)
    fun failoverDirect(): Boolean
    fun setFailoverDirect(value: Boolean)

    /** 首次使用时做旧配置迁移或预置占位 profile；已存在时不做任何事。 */
    fun ensureSeeded()

    /** 清空新旧全部代理配置（重置网络设置用）。 */
    fun reset()
}
