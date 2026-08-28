package xyz.babyplatipus.ptunnel.vpn

import android.content.Context
import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.backend.TunnelActionHandler
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

/**
 * Stainless / light. Конфиг с сервера скармливается как есть —
 * Config.parse() понимает INI-текст из register_or_update_awg,
 * включая Jc/Jmin/Jmax/S1/S2/H1..H4.
 *
 * VpnService библиотека поднимает свой (AbstractBackend$VpnService),
 * PtunnelVpnService здесь не участвует.
 */
object AwgTunnel {

    private const val NAME = "putinatunnel"

    private var backend: Backend? = null

    /** Состояние туннеля — читает UI. */
    @Volatile
    var state: Tunnel.State = Tunnel.State.DOWN
        private set

    private val tunnel = object : Tunnel {
        override fun getName() = NAME
        override fun onStateChange(newState: Tunnel.State) {
            state = newState
            android.util.Log.d("ptunnel-awg", "state -> $newState")
        }
        override fun isIpv4ResolutionPreferred(): Boolean = true
        override fun isMetered(): Boolean = false
    }

    private val noopActions = object : TunnelActionHandler {
        override fun runPreUp(cmds: MutableCollection<String>) {}
        override fun runPostUp(cmds: MutableCollection<String>) {}
        override fun runPreDown(cmds: MutableCollection<String>) {}
        override fun runPostDown(cmds: MutableCollection<String>) {}
    }

    private fun backend(context: Context): Backend =
        backend ?: GoBackend(context.applicationContext, noopActions).also { backend = it }

    /** configText — то, что приехало с сервера без изменений. */
    /**
     * configText — то, что приехало с сервера, без изменений.
     * excluded — пакеты, которые идут МИМО туннеля; вписываются
     * в [Interface] как ExcludedApplications, дальше библиотека
     * сама разрулит их через addDisallowedApplication.
     */
    fun start(context: Context, configText: String, excluded: Set<String> = emptySet()) {
        val text = if (excluded.isEmpty()) configText
        else injectExcluded(configText, excluded)
        val cfg = Config.parse(ByteArrayInputStream(text.toByteArray()))
        backend(context).setState(tunnel, Tunnel.State.UP, cfg)
    }

    private fun injectExcluded(configText: String, excluded: Set<String>): String {
        val line = "ExcludedApplications = " + excluded.joinToString(", ")
        val out = StringBuilder()
        var inserted = false
        configText.lineSequence().forEach { raw ->
            out.appendLine(raw)
            // сразу после заголовка [Interface], до первого [Peer]
            if (!inserted && raw.trim().equals("[Interface]", ignoreCase = true)) {
                out.appendLine(line)
                inserted = true
            }
        }
        return out.toString()
    }

    suspend fun stopAndWait(context: Context) {
        if (backend == null) return
        runCatching {
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
        }
        // ждём, пока состояние реально станет DOWN
        repeat(30) {
            if (state == Tunnel.State.DOWN) return
            kotlinx.coroutines.delay(100)
        }
    }

    fun stop(context: Context) {
        runCatching {
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
        }
    }

    fun isUp(): Boolean = state == Tunnel.State.UP
}