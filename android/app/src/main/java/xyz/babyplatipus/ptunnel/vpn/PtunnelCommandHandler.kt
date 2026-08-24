package xyz.babyplatipus.ptunnel.vpn

import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.SystemProxyStatus

class PtunnelCommandHandler(
    private val onStop: () -> Unit
) : CommandServerHandler {
    override fun serviceReload() {}
    override fun serviceStop() = onStop()
    override fun writeDebugMessage(message: String) {
        android.util.Log.d("ptunnel-box", message)
    }
    override fun getSystemProxyStatus(): SystemProxyStatus =
        throw UnsupportedOperationException()
    override fun setSystemProxyEnabled(enabled: Boolean) {}
    override fun connectSSHAgent(): Int = throw UnsupportedOperationException()
    override fun triggerNativeCrash() {}
}