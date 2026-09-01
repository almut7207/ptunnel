package xyz.babyplatipus.ptunnel.vpn

import org.json.JSONArray
import org.json.JSONObject
import xyz.babyplatipus.ptunnel.data.model.Credentials

/**
 * Собирает конфиг sing-box из кредов, полученных с сервера.
 * Параметры (порт, SNI, pbk, sid) приезжают из vless://-ссылки,
 * так что при смене на нодах клиент подхватит новые без релиза.
 */
// Резолвим адрес ноды заранее: внутри туннеля резолвить его нечем,
// а системный резолвер через libbox мы не реализуем.
object SingBoxConfig {

    fun build(c: Credentials.Xray, excludedApps: Set<String>, ruleSetDir: String, availableSets: List<String>): String {
        val serverAddr = runCatching {
            java.net.InetAddress.getByName(c.host).hostAddress
        }.getOrNull() ?: c.host

        val tls = JSONObject()
            .put("enabled", true)
            .put("server_name", c.sni)
            .put("utls", JSONObject()
                .put("enabled", true)
                .put("fingerprint", c.fingerprint.ifBlank { "chrome" }))
            .put("reality", JSONObject()
                .put("enabled", true)
                .put("public_key", c.publicKey)
                .put("short_id", c.shortId))

        val proxy = JSONObject()
            .put("type", "vless")
            .put("tag", "proxy")
            .put("server", serverAddr)
            .put("server_port", c.port)
            .put("uuid", c.uuid)
            .put("flow", c.flow)
            .put("packet_encoding", "xudp")
            .put("tls", tls)

        val tun = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", JSONArray().put("172.19.0.1/30"))
            .put("mtu", 1500)
            .put("auto_route", true)
            .put("strict_route", false)
            //.put("stack", "gvisor")

        if (excludedApps.isNotEmpty()) {
            val excl = JSONArray()
            excl.put("xyz.babyplatipus.ptunnel")
            excludedApps.forEach { excl.put(it) }
            tun.put("exclude_package", excl)
            val arr = JSONArray()
            excludedApps.forEach { arr.put(it) }
            tun.put("exclude_package", arr)
        }

        return JSONObject()
            .put("log", JSONObject()
                .put("level", "trace")
                .put("output", "box.log")
                .put("timestamp", true))
            .put("inbounds", JSONArray().put(tun))
            .put("outbounds", JSONArray()
                .put(proxy)
                .put(JSONObject().put("type", "direct")
                    .put("tag", "direct")))
            .put("dns", JSONObject()
                .put("servers", JSONArray()
                    .put(JSONObject()
                        .put("type", "https").put("tag", "dns-direct")
                        .put("server", "77.88.8.8"))
                    .put(JSONObject()
                        .put("type", "https").put("tag", "dns-remote")
                        .put("server", "8.8.8.8").put("detour", "proxy")))
                .put("rules", JSONArray().apply {
                    val ruSets = listOf("geosite-ru", "my-direct").filter { it in availableSets }
                    if (ruSets.isNotEmpty()) {
                        put(JSONObject()
                            .put("rule_set", JSONArray().apply { ruSets.forEach { put(it) } })
                            .put("server", "dns-direct"))
                    }
                    put(JSONObject()
                        .put("domain_suffix", JSONArray()
                            .put(".ru").put(".рф").put(".su").put(".moscow"))
                        .put("server", "dns-direct"))
                    if ("geosite-blocked" in availableSets) {
                        put(JSONObject()
                            .put("rule_set", "geosite-blocked")
                            .put("server", "dns-remote"))
                    }
                })
                .put("final", "dns-direct")
                .put("strategy", "prefer_ipv4"))
            .put("route", JSONObject()
                .put("auto_detect_interface", true)
                .put("rule_set", JSONArray().apply {
                    availableSets.forEach { name ->
                        put(JSONObject()
                            .put("tag", name)
                            .put("type", "local")
                            .put("format", "binary")
                            .put("path", "$ruleSetDir/$name.srs"))
                    }
                })
                .put("rules", JSONArray().apply {
                    // приватные сети — мимо туннеля
                    put(JSONObject()
                        .put("ip_cidr", JSONArray()
                            .put("10.0.0.0/8").put("172.16.0.0/12")
                            .put("192.168.0.0/16").put("127.0.0.0/8"))
                        .put("outbound", "direct"))
                    val directSets = listOf(
                        "russia-ip", "russia-ip-v6", "direct-nodes", "geosite-ru", "my-direct"
                    ).filter { it in availableSets }
                    if (directSets.isNotEmpty()) {
                        put(JSONObject()
                            .put("rule_set", JSONArray().apply { directSets.forEach { put(it) } })
                            .put("outbound", "direct"))
                    }
                    if ("geosite-blocked" in availableSets) {
                        put(JSONObject()
                            .put("rule_set", "geosite-blocked")
                            .put("outbound", "proxy"))
                    }
                    put(JSONObject()
                        .put("domain_suffix", JSONArray()
                            .put(".ru").put(".рф").put(".su").put(".moscow"))
                        .put("outbound", "direct"))
                })
                .put("final", "proxy"))
            .toString()
    }
}