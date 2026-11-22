package sb.linux.client.platform.webview

import android.webkit.HttpAuthHandler
import sb.linux.client.data.AppNetwork

/**
 * 带认证代理时 WebView 的 407 处理：请求方是生效 profile 的 host 时，
 * 用 profile 里填的账号密码直接通过，不弹系统认证框。
 * 站点自身的 Basic 认证（host 不是代理）不在此处理，走默认行为。
 * 风险：密码存在 SharedPreferences 明文（项目现有惯例），只在用户
 * 自己填了认证的 profile 上才会被这样用。
 */
internal object ProxyAuthHandler {
    fun handle(handler: HttpAuthHandler, host: String?): Boolean {
        val profile = AppNetwork.activeProxyProfile() ?: return false
        if (!profile.hasAuth) return false
        if (!host.equals(profile.host, ignoreCase = true)) return false
        handler.proceed(profile.username, profile.password)
        return true
    }
}
