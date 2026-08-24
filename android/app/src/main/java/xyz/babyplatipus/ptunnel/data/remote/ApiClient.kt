package xyz.babyplatipus.ptunnel.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import org.json.JSONArray

/**
 * Клиент vpn-api.
 *
 * ФОРМАТ ОБМЕНА ПРЕДВАРИТЕЛЬНЫЙ — бэкенд под него ещё не написан.
 * Всё, что зависит от формата, собрано в этом файле, менять только тут.
 *
 *   POST {base}/client/tunnel
 *        {"device_id": "...", "platform": "android", "tariff": "stainless"}
 *     -> {"success": true, "tariff": "stainless", "config": "[Interface]..."}
 *        {"success": true, "tariff": "armor",     "link":   "vless://..."}
 *
 *   POST {base}/client/link/start
 *        {"device_id": "..."}
 *     -> {"success": true, "deeplink": "tg://resolve?domain=...&start=..."}
 *
 *   GET  {base}/client/endpoints
 *     -> {"endpoints": ["https://...:7232/api", "..."]}
 *
 * Ответ вида {"success": false, "error": "..."} обрабатывается как ошибка.
 */
object ApiClient {

    /** true — UI работает без бэкенда, отдаются правдоподобные заглушки. */
    var useMock: Boolean = false

    /**
     * Точки входа. Перебираются по порядку до первого ответа —
     * это и есть страховка от блокировки одного домена.
     * Список можно подменить с сервера через /client/endpoints,
     * не выкатывая обновление в стор.
     */
    @Volatile
    var endpoints: List<String> = listOf(
        "https://capybara.baby-platipus.xyz:7234/api"
    )

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    class ApiException(message: String) : Exception(message)

    // -----------------------------------------------------------------
    // Публичные вызовы
    // -----------------------------------------------------------------

    /**
     * Основной вызов пайплайна: сервер генерит ключи, кладёт их в базу
     * под dev_<deviceId> и возвращает готовый конфиг.
     *
     * @return пара (kind, payload), где kind = "awg" | "xray",
     *         payload = INI-текст либо vless://-ссылка
     */
    suspend fun requestTunnel(username: String, tariff: String): Pair<String, String> {
        if (useMock) return mockTunnel(tariff)

        val body = JSONObject()
            .put("tg_id", 0)
            .put("username", username)
            .put("tariff", tariff.uppercase())
            .put("ref", JSONObject.NULL)

        val json = post("/create_tunnel", body)

        if (!json.optBoolean("success", false)) {
            throw ApiException(json.optString("error", "сервер вернул ошибку"))
        }

        val text = json.optString("config_text")
        if (text.isBlank()) throw ApiException("пустой config_text")

        return if (text.startsWith("vless://")) "xray" to text else "awg" to text
    }

    /** Опрос: привязался ли номер к TG-аккаунту. */
    suspend fun checkPhone(phone: String): Pair<String, String>? {
        if (useMock) return null
        val json = get("/check_phone?phone=$phone")
        if (!json.optBoolean("linked", false)) return null
        val tgId = json.optString("tg_id")
        val username = json.optString("username")
        return if (tgId.isBlank() || username.isBlank()) null else tgId to username
    }

    /** Опрос: подтвердил ли юзер вход в боте. Возвращает (tg_id, username). */
    suspend fun checkAppLogin(code: String): Pair<String, String>? {
        val json = get("/app_login?code=$code")
        if (!json.optBoolean("linked", false)) return null
        val tgId = json.optString("tg_id")
        val username = json.optString("username")
        return if (tgId.isBlank() || username.isBlank()) null else tgId to username
    }

    /** Обновление списка точек входа. Ошибку глотаем — не критично. */
    suspend fun refreshEndpoints() {
        if (useMock) return
        runCatching {
            val json = get("/client/endpoints")
            val arr = json.optJSONArray("endpoints") ?: return
            val list = (0 until arr.length()).map { arr.getString(it) }
            if (list.isNotEmpty()) endpoints = list
        }
    }

    // -----------------------------------------------------------------
    // Транспорт: перебор точек входа
    // -----------------------------------------------------------------

    private suspend fun post(path: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            execute { base ->
                Request.Builder()
                    .url(base + path)
                    .post(body.toString().toRequestBody(JSON))
                    .header("Content-Type", "application/json")
                    .build()
            }
        }

    private suspend fun get(path: String): JSONObject =
        withContext(Dispatchers.IO) {
            execute { base ->
                Request.Builder().url(base + path).get().build()
            }
        }

    private fun execute(build: (String) -> Request): JSONObject {
        var last: Exception? = null
        for (base in endpoints) {
            try {
                http.newCall(build(base)).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw ApiException("HTTP ${resp.code}: ${text.take(200)}")
                    }
                    return JSONObject(text)
                }
            } catch (e: Exception) {
                last = e   // точка входа не ответила — пробуем следующую
            }
        }
        throw ApiException(last?.message ?: "нет доступных точек входа")
    }

    /** Все туннели юзера с сервера — для обзора и оплаты. */
    suspend fun userTunnels(username: String): List<JSONObject> {
        val json = get("/get_user_info?username=$username")
        if (!json.optBoolean("success", false)) {
            throw ApiException(json.optString("error", "не удалось получить список"))
        }
        val arr = json.optJSONArray("tunnels") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    /**
     * Создаёт счёт. mean: "SBP" | "CARD" | "crypto".
     * @return пара (pay_url, order_id)
     */
    suspend fun createPayment(
        username: String,
        amount: Int,
        tunnelTypes: List<String>,
        selectedIps: List<String>,
        mean: String
    ): Pair<String, String?> {
        val body = JSONObject()
            .put("username", username)
            .put("amount", amount)
            .put("tunnels", JSONArray().apply { tunnelTypes.forEach { put(it) } })
            .put("selected_ips", JSONArray().apply { selectedIps.forEach { put(it) } })
            .put("payment_mean", if (mean == "crypto") "CARD" else mean)

        val path = if (mean == "crypto") "/create_payment" else "/create_payment_lava"
        val json = post(path, body)

        val url = json.optString("pay_url").takeIf { it.isNotBlank() }
            ?: throw ApiException(json.optString("error", "касса не ответила"))
        return url to json.optString("order_id").takeIf { it.isNotBlank() }
    }

    /**
     * Ждёт подтверждения оплаты через SSE. Блокирует до события или таймаута.
     * @return true — оплата прошла
     */
    suspend fun awaitPayment(orderId: String): Boolean = withContext(Dispatchers.IO) {
        val base = endpoints.firstOrNull() ?: return@withContext false
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // SSE — держим соединение
            .build()

        val req = Request.Builder()
            .url("$base/subscribe/$orderId")
            .header("Accept", "text/event-stream")
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use false
                val source = resp.body?.source() ?: return@use false
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data:") && line.contains("success")) {
                        return@use true
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    // -----------------------------------------------------------------
    // Заглушки — чтобы UI жил, пока бэкенда нет
    // -----------------------------------------------------------------

    private fun mockTunnel(tariff: String): Pair<String, String> =
        if (tariff == "armor") {
            "xray" to (
                "vless://11111111-2222-3333-4444-555555555555" +
                    "@capybara.baby-platipus.xyz:443" +
                    "?security=reality&encryption=none" +
                    "&pbk=TArE-y8kzQcSDjeTaWbOk7nq2YAeE5FRdiXWsjSr-XM" +
                    "&fp=chrome&type=tcp&sni=kinopoisk.sport" +
                    "&sid=a4a920607861f761&flow=xtls-rprx-vision#armor-mock"
                )
        } else {
            "awg" to """
                [Interface]
                PrivateKey = MOCKPRIVATEKEYMOCKPRIVATEKEYMOCKPRIVATEKEY=
                Address = 10.8.13.37/32
                DNS = 10.255.255.254
                MTU = 1280
                Jc = 4
                Jmin = 40
                Jmax = 70
                S1 = 15
                S2 = 39
                H1 = 12345678
                H2 = 87654321
                H3 = 13572468
                H4 = 24681357

                [Peer]
                PublicKey = +HExscYbAhMsSujaUkh/j6IQp2DbmHt4OD31zkGOkEQ=
                Endpoint = capybara.baby-platipus.xyz:22028
                AllowedIPs = 0.0.0.0/0
                PersistentKeepalive = 20
            """.trimIndent()
        }
}
