package xyz.babyplatipus.ptunnel.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.babyplatipus.ptunnel.data.ConfigParsers
//import xyz.babyplatipus.ptunnel.data.Prefs
import xyz.babyplatipus.ptunnel.data.model.Credentials
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.system.OsConstants
import java.net.Inet6Address
import java.net.InterfaceAddress
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

/**
 * VPN-сервис для тарифа armor (VLESS + Reality через sing-box).
 *
 * Сервис САМ реализует PlatformInterface — так же, как в официальном
 * sing-box-for-android. Отдельный объект-обёртка не годится: gomobile
 * держит на него слабую ссылку, GC его собирает, и Go падает с SIGSEGV.
 *
 * Stainless идёт другим путём — через GoBackend в AwgTunnel,
 * у той библиотеки свой VpnService.
 */
class PtunnelVpnService : VpnService(), PlatformInterface {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var commandServer: CommandServer? = null
    private var tunFd: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_CONNECT = "xyz.babyplatipus.ptunnel.CONNECT"
        const val ACTION_DISCONNECT = "xyz.babyplatipus.ptunnel.DISCONNECT"
        const val EXTRA_EXCLUDED = "excluded_packages"
        private const val CHANNEL_ID = "ptunnel_vpn"
        private const val NOTIF_ID = 1
        const val EXTRA_CONFIG = "config_blob"
        const val ACTION_STOPPED = "xyz.babyplatipus.ptunnel.STOPPED"
        const val ACTION_STARTED = "xyz.babyplatipus.ptunnel.STARTED"
    }

    // -----------------------------------------------------------------
    // Жизненный цикл сервиса
    // -----------------------------------------------------------------

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> {
                val blob = intent?.getStringExtra(EXTRA_CONFIG)
                val excluded = intent?.getStringArrayListExtra(EXTRA_EXCLUDED) ?: arrayListOf()
                startTunnel(blob, excluded.toSet())
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(blob: String?, excluded: Set<String>) {
        startForegroundCompat()
        VpnStateHolder.setConnecting()

        scope.launch {
            try {
                val creds = ConfigParsers.deserialize(blob)
                if (creds !is Credentials.Xray) {
                    throw IllegalStateException("нет xray-кредов")
                }
                startSingBox(creds, excluded)
                sendBroadcast(Intent(ACTION_STARTED).setPackage(packageName))
                VpnStateHolder.setConnected()
            } catch (e: Exception) {
                android.util.Log.e("ptunnel-box", "startTunnel failed", e)
                VpnStateHolder.setError(e.message ?: "tunnel error")
                stopTunnel()
            }
        }
    }

    private fun startSingBox(creds: Credentials.Xray, excluded: Set<String>) {
        val sets = xyz.babyplatipus.ptunnel.data.RuleSets.install(this)
        val dir = xyz.babyplatipus.ptunnel.data.RuleSets.dir(this).absolutePath
        val cfg = SingBoxConfig.build(creds, excluded, dir, sets)
        android.util.Log.d("ptunnel-box", "config: $cfg")

        val existing = commandServer
        if (existing != null) {
            android.util.Log.d("ptunnel-box", "reloading existing server")
            existing.startOrReloadService(cfg, OverrideOptions())
            return
        }

        val server = CommandServer(PtunnelCommandHandler { stopTunnel() }, this)
        server.start()
        commandServer = server
        server.startOrReloadService(cfg, OverrideOptions())
        android.util.Log.d("ptunnel-box", "startOrReloadService OK")
    }

    private fun stopTunnel() {
        // Синхронно — иначе scope.cancel() в onDestroy оборвёт
        // закрытие на полпути и оставит Go-объект в мёртвом состоянии
        runCatching { commandServer?.closeService() }
        runCatching { tunFd?.close() }
        tunFd = null
        runCatching { commandServer?.close() }
        commandServer = null

        VpnStateHolder.setDisconnected()
        stopForegroundCompat()
        sendBroadcast(Intent(ACTION_STOPPED).setPackage(packageName))
        stopSelf()
    }

    override fun onRevoke() {
        stopTunnel()
    }

    override fun onDestroy() {
        runCatching { commandServer?.closeService() }
        runCatching { commandServer?.close() }
        commandServer = null
        runCatching { tunFd?.close() }
        tunFd = null
        scope.cancel()
        super.onDestroy()
    }

    // -----------------------------------------------------------------
    // PlatformInterface — то, что ядро запрашивает у Android
    // -----------------------------------------------------------------

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("нет разрешения на VPN")

        val builder = Builder()
            .setSession("PutinaTunnel")
            .setMtu(options.mtu)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        val inet4 = options.inet4Address
        while (inet4.hasNext()) {
            val a = inet4.next()
            builder.addAddress(a.address(), a.prefix())
        }

        val inet6 = options.inet6Address
        while (inet6.hasNext()) {
            val a = inet6.next()
            builder.addAddress(a.address(), a.prefix())
        }

        if (options.autoRoute) {
            runCatching {
                val dns = options.dnsServerAddress
                while (dns.hasNext()) builder.addDnsServer(dns.next())
            }

            val r4 = options.inet4RouteAddress
            if (r4.hasNext()) {
                while (r4.hasNext()) {
                    val a = r4.next()
                    builder.addRoute(a.address(), a.prefix())
                }
            } else {
                builder.addRoute("0.0.0.0", 0)
            }

            val r6 = options.inet6RouteAddress
            while (r6.hasNext()) {
                val a = r6.next()
                builder.addRoute(a.address(), a.prefix())
            }

            val include = options.includePackage
            while (include.hasNext()) {
                runCatching { builder.addAllowedApplication(include.next()) }
            }

            val exclude = options.excludePackage
            while (exclude.hasNext()) {
                runCatching { builder.addDisallowedApplication(exclude.next()) }
            }
        }

        val pfd = builder.establish() ?: error("establish() вернул null")
        tunFd = pfd
        android.util.Log.d("ptunnel-box", "openTun -> fd=${pfd.fd}")
        return pfd.fd
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    override fun includeAllNetworks(): Boolean = false
    override fun underNetworkExtension(): Boolean = false
    override fun usePlatformBridge(): Boolean = false
    override fun usePlatformShell(): Boolean = false

    override fun getInterfaces(): NetworkInterfaceIterator {
        val cm = getSystemService(ConnectivityManager::class.java)
        val sysIfaces = java.net.NetworkInterface.getNetworkInterfaces().toList()
        val result = mutableListOf<LibboxNetworkInterface>()

        for (network in cm.allNetworks) {
            val lp = cm.getLinkProperties(network) ?: continue
            val caps = cm.getNetworkCapabilities(network) ?: continue
            val sysIface = sysIfaces.find { it.name == lp.interfaceName } ?: continue

            val bi = LibboxNetworkInterface()
            bi.name = lp.interfaceName
            bi.index = sysIface.index
            runCatching { bi.mtu = sysIface.mtu }

            bi.dnsServer = StringArray(
                lp.dnsServers.mapNotNull { it.hostAddress }.iterator()
            )
            bi.gateway = StringArray(
                lp.routes.filter { it.destination.prefixLength == 0 }
                    .mapNotNull { it.gateway }
                    .filterNot { it.isAnyLocalAddress }
                    .mapNotNull { it.hostAddress }
                    .iterator()
            )
            bi.addresses = StringArray(
                sysIface.interfaceAddresses.map { toPrefix(it) }.iterator()
            )

            bi.type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }

            var flags = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (sysIface.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
            if (sysIface.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
            if (sysIface.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
            bi.flags = flags

            bi.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            result.add(bi)
        }
        return InterfaceArray(result.iterator())
    }

    private fun toPrefix(ia: InterfaceAddress): String =
        if (ia.address is Inet6Address) {
            "${Inet6Address.getByAddress(ia.address.address).hostAddress}/${ia.networkPrefixLength}"
        } else {
            "${ia.address.hostAddress}/${ia.networkPrefixLength}"
        }

    override fun findConnectionOwner(
        ipProto: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int
    ): ConnectionOwner = throw UnsupportedOperationException()

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.start(this, listener)
    }
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.stop()
    }
    override fun startNeighborMonitor(listener: NeighborUpdateListener) {}
    override fun closeNeighborMonitor(listener: NeighborUpdateListener) {}

    override fun clearDNSCache() {}
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun readWIFIState(): WIFIState? = null
    override fun registerMyInterface(name: String) {}

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification) {}
    override fun cancelNotification(identifier: String, typeID: Int) {}

    override fun checkPlatformShell() = throw UnsupportedOperationException()
    override fun createBridge(options: BridgeOptions): BridgeSession =
        throw UnsupportedOperationException()
    override fun lookupSFTPServer(): String = throw UnsupportedOperationException()
    override fun lookupUser(name: String): PlatformUser = throw UnsupportedOperationException()
    override fun openShellSession(
        user: PlatformUser, s: String, args: StringIterator, s2: String, i: Int, i2: Int
    ): ShellSession = throw UnsupportedOperationException()
    override fun readSystemSSHHostKey(): String = throw UnsupportedOperationException()
    override fun tailscaleHostname(): String = ""

    // -----------------------------------------------------------------
    // Уведомление
    // -----------------------------------------------------------------

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PutinaTunnel")
            .setContentText("Туннель активен")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }
}