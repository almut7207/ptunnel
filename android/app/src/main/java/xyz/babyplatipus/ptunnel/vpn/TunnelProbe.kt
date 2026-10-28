package xyz.babyplatipus.ptunnel.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Проверка туннеля по факту прохождения трафика.
 *
 * Критерий: запрос к заблокированному в РФ ресурсу должен выйти
 * с адреса зарубежной прокси-ноды. Установившееся TCP-соединение
 * само по себе ничего не доказывает — оно может пойти мимо туннеля,
 * пока маршруты не встали.
 */
object TunnelProbe {

    sealed class Result {
        data class Ok(val exitIp: String) : Result()
        object ServerUnreachable : Result()
        object Blocked : Result()
        data class Failed(val message: String) : Result()
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .build()

    /**
     * @param exitIps адреса зарубежных прокси-нод из /api/exit_ips.
     *                Пустой набор — сверять не с чем, засчитываем любой ответ.
     */
    suspend fun run(exitIps: Set<String>, patient: Boolean = false): Result =
        withContext(Dispatchers.IO) {
            var lastIp: String? = null

            // после миграции данные пойдут только когда сервер переключит пир,
            // это до 70 секунд — первые неответы не считаем неудачей
            val attempts = if (patient) 15 else 4
            val pause = if (patient) 6000L else 2000L

            delay(2500)
            repeat(attempts) { attempt ->
                if (attempt > 0) delay(pause)
                val ip = withTimeoutOrNull(8_000) { fetchExitIp() }
                if (ip != null) {
                    lastIp = ip
                    android.util.Log.d("ptunnel-box", "probe $attempt -> $ip")
                    if (exitIps.isEmpty() || ip in exitIps) {
                        // exit-IP совпал, но маршруты могли ещё не встать —
                        // проверяем, что заблокированный ресурс реально открывается
                        if (blockedReachable()) {
                            return@withContext Result.Ok(ip)
                        }
                        android.util.Log.d("ptunnel-box", "exit-IP верный, но трафик не идёт")
                    }
                }
            }
            lastIp?.let { return@withContext Result.Blocked }
            Result.ServerUnreachable
        }

    /** Проверяем ресурс, который без туннеля недоступен из РФ. */
    private suspend fun blockedReachable(): Boolean = runCatching {
        withTimeoutOrNull(8_000) {
            val req = Request.Builder()
                .url("https://www.youtube.com/generate_204")
                .head()
                .build()
            http.newCall(req).execute().use { it.isSuccessful || it.code == 204 }
        } ?: false
    }.getOrDefault(false)
    /**
     * Спрашиваем внешний сервис, с какого адреса мы пришли.
     * Три источника подряд — если один недоступен, пробуем следующий.
     */
    private fun fetchExitIp(): String? {
        val urls = listOf(
            "https://api.ipify.org",
            "https://ifconfig.me/ip",
            "https://icanhazip.com"
        )
        for (url in urls) {
            val ip = runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (r.isSuccessful) r.body?.string()?.trim() else null
                }
            }.getOrNull()
            if (!ip.isNullOrBlank() && ip.count { it == '.' } == 3) return ip
        }
        return null
    }
}