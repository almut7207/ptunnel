package xyz.babyplatipus.ptunnel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File

/**
 * onCreate вызывается в КАЖДОМ процессе приложения.
 * Libbox нужен только в :singbox — в главном процессе он не только
 * бесполезен, но и опасен: рядом с Go-рантаймом AmneziaWG это роняет процесс.
 */
class PtunnelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!currentProcessName().endsWith(":singbox")) return

        runCatching {
            go.Seq.setContext(applicationContext)
            Libbox.setup(SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = File(filesDir, "work").apply { mkdirs() }.absolutePath
                tempPath = cacheDir.absolutePath
                fixAndroidStack = true
            })
            android.util.Log.d("ptunnel-box", "Libbox.setup done in :singbox")
        }.onFailure {
            android.util.Log.e("ptunnel-box", "Libbox.setup failed", it)
        }
    }

    private fun currentProcessName(): String {
        if (android.os.Build.VERSION.SDK_INT >= 28) return getProcessName()
        val pid = android.os.Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: ""
    }
}