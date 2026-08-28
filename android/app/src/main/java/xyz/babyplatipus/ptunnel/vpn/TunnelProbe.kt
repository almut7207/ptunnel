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
    suspend fun run(exitIps: Set<String>): Result = withContext(Dispatchers.IO) {
        var lastIp: String? = null

        // ядру нужно время, чтобы поднять tun и перехватить маршруты
        delay(6000)

        repeat(10) { attempt ->
            if (attempt > 0) delay(3000)
            val ip = withTimeoutOrNull(15_000) { fetchExitIp() }
            if (ip != null) {
                lastIp = ip
                android.util.Log.d("ptunnel-box", "probe $attempt -> $ip")
                if (exitIps.isEmpty() || ip in exitIps) {
                    return@withContext Result.Ok(ip)
                }
            }
        }

        // ответ есть, но адрес чужой — трафик идёт мимо туннеля
        lastIp?.let {
            android.util.Log.w("ptunnel-box", "exit ip $it не из списка прокси-нод")
            return@withContext Result.Blocked
        }
        Result.ServerUnreachable
    }

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