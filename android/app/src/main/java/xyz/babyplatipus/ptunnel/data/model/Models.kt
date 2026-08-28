package xyz.babyplatipus.ptunnel.data.model

/** Тариф на экране выбора. */
data class Tariff(
    val code: String,
    val title: String,
    val subtitle: String,
    val priceRub: Int,
    val protocol: String
) {
    companion object {
        val STAINLESS = Tariff(
            code = "stainless",
            title = "STAINLESS",
            subtitle = "AmneziaWG — быстрый, для обычных блокировок",
            priceRub = 350,
            protocol = "awg"
        )
        val ARMOR = Tariff(
            code = "armor",
            title = "ARMOR",
            subtitle = "VLESS + Reality — проходит там, где не проходит остальное",
            priceRub = 1000,
            protocol = "xray"
        )
        val ALL = listOf(STAINLESS, ARMOR)
    }
}

/**
 * Креды, полученные с сервера и разобранные в поля.
 * Дальше уходят прямо в движок. Приложение хранит их у себя
 * и больше ни в какой базе себя не ищет.
 */
sealed class Credentials {

    /** AmneziaWG — разобранный INI-конфиг от register_or_update_awg. */
    data class Awg(
        val privateKey: String,
        val address: String,
        val dns: String,
        val mtu: Int,
        val serverPublicKey: String,
        val endpointHost: String,
        val endpointPort: Int,
        val allowedIps: String,
        val persistentKeepalive: Int,
        val jc: Int, val jmin: Int, val jmax: Int,
        val s1: Int, val s2: Int,
        val h1: Long, val h2: Long, val h3: Long, val h4: Long,
        val rawConfig: String
    ) : Credentials()

    /** VLESS + Reality — разобранная vless:// ссылка от register_or_update_xray. */
    data class Xray(
        val uuid: String,
        val host: String,
        val port: Int,
        val sni: String,
        val publicKey: String,
        val shortId: String,
        val flow: String,
        val fingerprint: String,
        val rawLink: String
    ) : Credentials()
}

/** Шаги пайплайна — их приложение рисует на экране по мере прохождения. */
enum class Stage {
    REQUESTING_API,
    KEYS_RECEIVED,
    CONFIGURING_ENGINE,
    ASKING_PERMISSION,
    CONNECTING,
    ROUTING_TRAFFIC,
    VERIFYING,
    DONE
}

data class StageLine(
    val stage: Stage,
    val label: String,
    val status: Status
) {
    enum class Status { PENDING, RUNNING, OK, FAILED }
}

/**
 * Туннель в списке. local = конфиг есть на этом устройстве,
 * значит можно подключиться; иначе только оплата.
 */
data class TunnelInfo(
    val id: String,
    val type: String,
    val balanceMinutes: Int,
    val active: Boolean,
    val local: Boolean,
    val tariff: String?
)

/** Состояние всего экрана подключения. */
data class ConnectState(
    val tariff: Tariff? = null,
    val lines: List<StageLine> = emptyList(),
    val credentials: Credentials? = null,
    val connected: Boolean = false,
    val error: String? = null,
    val showTelegramPrompt: Boolean = false,
    val offline: Boolean = false,
    val telegramDeeplink: String? = null
)

/** Установленное приложение в списке раздельных туннелей. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val excluded: Boolean
)

/** Состояние туннеля. Используется VpnStateHolder и PtunnelVpnService. */
enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Конфиг, который сервис отдаёт движку.
 * Раньше приезжал с сервера одним JSON; теперь собирается
 * из Credentials после разбора awg-INI или vless-ссылки.
 */
data class ClientConfig(
    val protocol: String,
    val endpoint: String,
    val address: String,
    val dns: String,
    val publicKey: String,
    val privateKey: String = "",
    val presharedKey: String = "",
    val sni: String = "",
    val shortId: String = "",
    val flow: String = "",
    val mtu: Int = 1280,
    val jc: Int = 0, val jmin: Int = 0, val jmax: Int = 0,
    val s1: Int = 0, val s2: Int = 0,
    val h1: Long = 0, val h2: Long = 0, val h3: Long = 0, val h4: Long = 0
)
