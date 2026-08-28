package xyz.babyplatipus.ptunnel.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Следит за наличием интернета в главном процессе.
 * Нужен, чтобы показать пользователю, что связи нет, и восстановить
 * туннель, когда сеть вернётся.
 */
object NetworkWatcher {

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {
        if (callback != null) return
        val manager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java)
        cm = manager
        _online.value = check(manager)

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _online.value = check(manager)
            }
            override fun onLost(network: Network) {
                _online.value = check(manager)
            }
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
                _online.value = check(manager)
            }
        }
        callback = cb
        runCatching { manager.registerDefaultNetworkCallback(cb) }
    }

    /** Есть ли сеть, не считая нашего же туннеля. */
    private fun check(manager: ConnectivityManager): Boolean =
        manager.allNetworks.any { n ->
            val caps = manager.getNetworkCapabilities(n) ?: return@any false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
}