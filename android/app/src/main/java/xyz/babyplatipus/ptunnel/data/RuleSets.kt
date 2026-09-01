package xyz.babyplatipus.ptunnel.data

import android.content.Context
import java.io.File

/**
 * Копирует .srs-листы из assets в filesDir при первом запуске.
 * Позже сюда же ляжет обновление с сервера — тогда файлы будут
 * перезаписываться, а sing-box подхватит их при следующем старте.
 */
object RuleSets {

    val NAMES = listOf(
        "geosite-ru",
        "my-direct",
        "geosite-blocked",
        "russia-ip",
        "russia-ip-v6",
        "direct-nodes"
    )

    fun dir(context: Context): File =
        File(context.filesDir, "rulesets").apply { mkdirs() }

    fun path(context: Context, name: String): String =
        File(dir(context), "$name.srs").absolutePath

    /** Возвращает список реально доступных листов. */
    fun install(context: Context): List<String> {
        val target = dir(context)
        val ok = mutableListOf<String>()
        for (name in NAMES) {
            val file = File(target, "$name.srs")
            if (!file.exists()) {
                runCatching {
                    context.assets.open("rulesets/$name.srs").use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                }.onFailure {
                    android.util.Log.w("ptunnel-box", "нет листа $name: ${it.message}")
                }
            }
            if (file.exists()) ok.add(name)
        }
        return ok
    }
}