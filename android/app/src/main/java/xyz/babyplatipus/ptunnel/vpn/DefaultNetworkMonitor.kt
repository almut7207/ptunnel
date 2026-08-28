package xyz.babyplatipus.ptunnel.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import io.nekohasekai.libbox.InterfaceUpdateListener
import java.net.NetworkInterface

/**
 * Сообщает ядру, какой интерфейс сейчас основной.
 * Без этого auto_detect_interface не находит выхода наружу
 * и любое соединение падает с "no available network interface".
 */
object DefaultNetworkMonitor {

    private var listener: InterfaceUpdateListener? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var cm: ConnectivityManager? = null

    fun start(context: Context, l: InterfaceUpdateListener) {
        listener = l
        val manager = context.getSystemService(ConnectivityManager::class.java)
        cm = manager

        update(manager.activeNetwork)

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                update(network)
            }
            override fun onLost(network: Network) {
                update(manager.activeNetwork)
            }
            override fun onCapabilitiesChanged(
                network: Network,
                caps: android.net.NetworkCapabilities
            ) {
                update(network)
            }
        }
        callback = cb
        runCatching { manager.registerDefaultNetworkCallback(cb) }
    }

    fun stop() {
        callback?.let { cb -> runCatching { cm?.unregisterNetworkCallback(cb) } }
        callback = null
        listener = null
        cm = null
    }

    private fun update(network: Network?) {
        val l = listener ?: return
        if (network == null) {
            android.util.Log.d("ptunnel-box", "сеть пропала")
            runCatching { l.updateDefaultInterface("", -1, false, false) }
            return
        }
        repeat(10) {
            val lp = cm?.getLinkProperties(network)
            val name = lp?.interfaceName
            if (name != null && !name.startsWith("tun")) {
                val idx = runCatching { NetworkInterface.getByName(name)?.index }.getOrNull()
                if (idx != null) {
                    android.util.Log.d("ptunnel-box", "интерфейс: $name idx=$idx")
                    runCatching { l.updateDefaultInterface(name, idx, false, false) }
                    return
                }
            }
            Thread.sleep(100)
        }
    }
}