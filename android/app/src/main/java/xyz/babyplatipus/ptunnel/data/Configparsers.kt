package xyz.babyplatipus.ptunnel.data

import android.net.Uri
import xyz.babyplatipus.ptunnel.data.model.Credentials

/**
 * Сервер отдаёт то же самое, что уходит в бота и на сайт:
 *   stainless -> INI-текст конфига (register_or_update_awg)
 *   armor     -> vless://... ссылка   (register_or_update_xray)
 *
 * Здесь они разбираются в поля, которые понимает движок.
 * Никакого своего формата придумывать не пришлось — переиспользуем
 * то, что бэкенд уже умеет отдавать.
 */
object ConfigParsers {

    // -----------------------------------------------------------------
    // AmneziaWG: [Interface] / [Peer]
    // -----------------------------------------------------------------

    fun parseAwg(configText: String): Credentials.Awg {
        val iface = HashMap<String, String>()
        val peer = HashMap<String, String>()
        var current: HashMap<String, String>? = null

        configText.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#") -> Unit
                line.equals("[Interface]", true) -> current = iface
                line.equals("[Peer]", true) -> current = peer
                line.contains("=") -> {
                    val k = line.substringBefore("=").trim()
                    val v = line.substringAfter("=").trim()
                    current?.put(k.lowercase(), v)
                }
            }
        }

        val endpoint = peer["endpoint"].orEmpty()
        val host = endpoint.substringBeforeLast(":", endpoint)
        val port = endpoint.substringAfterLast(":", "").toIntOrNull() ?: 22028

        fun i(map: Map<String, String>, key: String, def: Int) =
            map[key]?.toIntOrNull() ?: def

        fun l(map: Map<String, String>, key: String, def: Long) =
            map[key]?.toLongOrNull() ?: def

        return Credentials.Awg(
            privateKey = iface["privatekey"].orEmpty(),
            address = iface["address"].orEmpty(),
            dns = iface["dns"] ?: "10.255.255.254",
            mtu = i(iface, "mtu", 1280),
            serverPublicKey = peer["publickey"].orEmpty(),
            endpointHost = host,
            endpointPort = port,
            allowedIps = peer["allowedips"] ?: "0.0.0.0/0",
            persistentKeepalive = i(peer, "persistentkeepalive", 20),
            jc = i(iface, "jc", 4),
            jmin = i(iface, "jmin", 40),
            jmax = i(iface, "jmax", 70),
            s1 = i(iface, "s1", 15),
            s2 = i(iface, "s2", 39),
            h1 = l(iface, "h1", 12345678L),
            h2 = l(iface, "h2", 87654321L),
            h3 = l(iface, "h3", 13572468L),
            h4 = l(iface, "h4", 24681357L),
            rawConfig = configText
        )
    }

    // -----------------------------------------------------------------
    // VLESS + Reality: vless://uuid@host:port?params#label
    // -----------------------------------------------------------------

    fun parseVless(link: String): Credentials.Xray {
        val uri = Uri.parse(link)
        val userInfo = uri.userInfo.orEmpty()
        val host = uri.host.orEmpty()
        val port = if (uri.port > 0) uri.port else 443

        fun q(name: String, def: String = "") =
            uri.getQueryParameter(name) ?: def

        return Credentials.Xray(
            uuid = userInfo,
            host = host,
            port = port,
            sni = q("sni"),
            publicKey = q("pbk"),
            shortId = q("sid"),
            flow = q("flow", "xtls-rprx-vision"),
            fingerprint = q("fp", "chrome"),
            rawLink = link
        )
    }

    /**
     * Сериализация обратно в строку — чтобы хранить в DataStore
     * одним полем и не городить схему.
     */
    fun serialize(c: Credentials): String = when (c) {
        is Credentials.Awg -> "awg\n" + c.rawConfig
        is Credentials.Xray -> "xray\n" + c.rawLink
    }

    fun deserialize(blob: String?): Credentials? {
        if (blob.isNullOrBlank()) return null
        val kind = blob.substringBefore("\n")
        val body = blob.substringAfter("\n")
        return runCatching {
            when (kind) {
                "awg" -> parseAwg(body)
                "xray" -> parseVless(body.trim())
                else -> null
            }
        }.getOrNull()
    }
}
