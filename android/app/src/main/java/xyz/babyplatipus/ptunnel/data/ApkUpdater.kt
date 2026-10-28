package xyz.babyplatipus.ptunnel.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Скачивает APK обновления в кэш. Устанавливает его система —
 * молча подменить приложение Android не позволяет.
 */
object ApkUpdater {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "update.apk")

        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body ?: return@runCatching null
                val total = body.contentLength()
                var done = 0L

                body.byteStream().use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress((done * 100 / total).toInt())
                        }
                    }
                }
                target
            }
        }.getOrNull()
    }
}