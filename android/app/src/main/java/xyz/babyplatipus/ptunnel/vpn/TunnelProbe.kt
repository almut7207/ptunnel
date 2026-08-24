package xyz.babyplatipus.ptunnel.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Проверка, что туннель реально пропускает трафик.
 * Флаг "подключено" ставится только после успешной пробы.
 */
object TunnelProbe {

    sealed class Result {
        object Ok : Result()
        object ServerUnreachable : Result()   // нода не отвечает
        object Blocked : Result()             // туннель поднят, трафик не идёт
        data class Failed(val message: String) : Result()
    }

    /**
     * @param host адрес ноды (для проверки доступности сервера)
     * @param port порт ноды
     */
    suspend fun run(host: String, port: Int): Result = withContext(Dispatchers.IO) {
        // 1. Доступна ли нода вообще
        val serverOk = withTimeoutOrNull(6000) {
            runCatching {
                Socket().use { it.connect(InetSocketAddress(host, port), 5000) }
                true
            }.getOrDefault(false)
        } ?: false

        if (!serverOk) return@withContext Result.ServerUnreachable

        // 2. Идёт ли трафик через туннель. Три попытки — ядру нужно
        //    время на первый хендшейк.
        repeat(3) { attempt ->
            if (attempt > 0) delay(1500)
            val through = withTimeoutOrNull(8000) {
                runCatching {
                    Socket().use { s ->
                        s.connect(InetSocketAddress("1.1.1.1", 443), 6000)
                        s.isConnected
                    }
                }.getOrDefault(false)
            } ?: false
            if (through) return@withContext Result.Ok
        }

        Result.Blocked
    }
}