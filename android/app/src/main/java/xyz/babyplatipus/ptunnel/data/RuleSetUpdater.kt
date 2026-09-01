package xyz.babyplatipus.ptunnel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Обновление .srs-листов с сервера.
 *
 * Сервер отдаёт манифест:
 *   GET {base}/rulesets/manifest.json
 *   {"version": 42, "format": "1.14", "files": {"geosite-ru": "sha256...", ...}}
 * и сами файлы: GET {base}/rulesets/geosite-ru.srs
 *
 * Компилировать на устройстве нельзя — rule-set compile есть только в CLI,
 * в libbox его нет. Поэтому сервер отдаёт уже готовые файлы, а клиент
 * сверяет хеши и качает изменившееся.
 */
object RuleSetUpdater {

    private const val FORMAT = "1.14"
    private const val PERIOD_MS = 7L * 24 * 60 * 60 * 1000   // раз в неделю

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Синхронизация не чаще раза в неделю. */
    suspend fun syncIfDue(dir: File, prefs: Prefs): Boolean {
        val last = prefs.ruleSetSyncedAt()
        android.util.Log.d("ptunnel", "syncIfDue last=$last")
        if (System.currentTimeMillis() - last < PERIOD_MS) return false

        val result = sync(dir, prefs)
        // отметку ставим, только если манифест реально прочитался,
        // иначе при недоступном сервере следующая попытка будет через неделю
        if (result != null) prefs.markRuleSetSynced()
        return result == true
    }

    suspend fun sync(dir: File, prefs: Prefs): Boolean? = withContext(Dispatchers.IO) {
        val base = xyz.babyplatipus.ptunnel.data.remote.ApiClient.endpoints.firstOrNull()
            ?: return@withContext null
        android.util.Log.d("ptunnel", "sync base=$base")

        val manifest = runCatching {
            val req = Request.Builder().url("$base/rulesets/manifest.json").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                JSONObject(resp.body?.string().orEmpty())
            }
        }.getOrNull() ?: return@withContext null

        val format = manifest.optString("format")
        if (format.isNotBlank() && format != FORMAT) {
            android.util.Log.w("ptunnel", "листы собраны под $format, у нас $FORMAT")
            return@withContext false
        }

        val files = manifest.optJSONObject("files") ?: return@withContext false
        val known = prefs.ruleSetHashes()
        var changed = false

        files.keys().forEach { name ->
            val hash = files.optString(name)
            val target = File(dir, "$name.srs")
            if (known[name] == hash && target.exists()) return@forEach

            val ok = runCatching {
                val req = Request.Builder().url("$base/rulesets/$name.srs").build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching false
                    val tmp = File(dir, "$name.srs.tmp")
                    resp.body?.byteStream()?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                    tmp.renameTo(target)
                }
            }.getOrDefault(false)

            if (ok) {
                android.util.Log.d("ptunnel", "обновлён лист $name")
                changed = true
                prefs.saveRuleSetHash(name, hash)
            }
        }
        changed
    }
}