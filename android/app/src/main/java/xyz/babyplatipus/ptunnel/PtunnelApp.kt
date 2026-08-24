package xyz.babyplatipus.ptunnel

import android.app.Application
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import java.io.File

/**
 * Libbox.setup() вызывается ОДИН раз на процесс, а не при каждом
 * старте туннеля — повторный setup на живом Go-рантайме роняет процесс.
 */
class PtunnelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            go.Seq.setContext(applicationContext)
            Libbox.setup(SetupOptions().apply {
                basePath = filesDir.absolutePath
                workingPath = File(filesDir, "work").apply { mkdirs() }.absolutePath
                tempPath = cacheDir.absolutePath
                fixAndroidStack = true
                android.util.Log.d("ptunnel-box", "Libbox.setup done")
            })
        }.onFailure {
            android.util.Log.e("ptunnel-box", "Libbox.setup failed", it)
        }
    }
}