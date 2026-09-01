package xyz.babyplatipus.ptunnel.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Импорт конфигов, созданных до появления приложения.
 *
 * Приватный ключ AWG существует только в .conf-файле у клиента —
 * на сервере его нет. Поэтому единственный способ перенести старый
 * туннель в приложение: прочитать файл с устройства.
 *
 * Доступ через ACTION_OPEN_DOCUMENT_TREE: пользователь сам указывает
 * папку, приложение видит только её.
 */
object ConfigImporter {

    data class Result(
        val imported: Int,
        val scanned: Int,
        val skipped: List<String>
    )

    /**
     * @param knownIds ip/uuid туннелей юзера с сервера — импортируем
     *                 только совпадения, чужие файлы игнорируем
     */
    suspend fun scanFolder(
        context: Context,
        treeUri: Uri,
        knownIds: Set<String>,
        store: TunnelStore
    ): Result = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext Result(0, 0, emptyList())

        var scanned = 0
        var imported = 0
        val skipped = mutableListOf<String>()

        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > 3) return
            dir.listFiles().forEach { f ->
                when {
                    f.isDirectory -> walk(f, depth + 1)
                    f.name?.endsWith(".conf", true) == true -> {
                        scanned++
                        val text = runCatching {
                            context.contentResolver.openInputStream(f.uri)
                                ?.bufferedReader()?.use { it.readText() }
                        }.getOrNull() ?: return@forEach

                        val creds = runCatching { ConfigParsers.parseAwg(text) }.getOrNull()
                            ?: return@forEach
                        if (creds.privateKey.isBlank()) return@forEach

                        val ip = creds.address.substringBefore("/")
                        if (ip.isBlank()) return@forEach

                        if (ip in knownIds) {
                            kotlinx.coroutines.runBlocking {
                                store.save(LocalTunnel(
                                    id = ip,
                                    tariff = if (creds.jc > 0) "stainless" else "light",
                                    blob = ConfigParsers.serialize(creds),
                                    createdAt = System.currentTimeMillis()
                                ))
                            }
                            imported++
                        } else {
                            skipped.add(f.name ?: ip)
                        }
                    }
                }
            }
        }

        walk(root, 0)
        Result(imported, scanned, skipped)
    }

    /**
     * Импорт файлов, выбранных пользователем вручную через файловый пикер.
     * В отличие от scanFolder, не требует доступа к папке — Android даёт
     * права на конкретные файлы.
     */
    suspend fun importFiles(
        context: Context,
        uris: List<Uri>,
        knownIds: Set<String>,
        store: TunnelStore
    ): Result = withContext(Dispatchers.IO) {
        var imported = 0
        val skipped = mutableListOf<String>()

        for (uri in uris) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
            }.getOrNull() ?: continue

            val creds = runCatching { ConfigParsers.parseAwg(text) }.getOrNull()
            if (creds == null || creds.privateKey.isBlank()) {
                skipped.add(uri.lastPathSegment ?: "?")
                continue
            }

            val ip = creds.address.substringBefore("/")
            if (ip.isNotBlank() && ip in knownIds) {
                store.save(LocalTunnel(
                    id = ip,
                    tariff = if (creds.jc > 0) "stainless" else "light",
                    blob = ConfigParsers.serialize(creds),
                    createdAt = System.currentTimeMillis()
                ))
                imported++
            } else {
                skipped.add(uri.lastPathSegment ?: ip)
            }
        }
        Result(imported, uris.size, skipped)
    }

    /** Импорт vless-ссылки из буфера — для armor. */
    /**
     * Импорт из буфера: понимает и vless://-ссылку (armor),
     * и vpn://import/<base64> от Amnezia (stainless), и голый INI-текст.
     */
    suspend fun importFromText(
        raw: String,
        knownIds: Set<String>,
        store: TunnelStore
    ): Boolean = withContext(Dispatchers.IO) {
        val text = raw.trim()

        // armor
        if (text.startsWith("vless://")) {
            val creds = runCatching { ConfigParsers.parseVless(text) }.getOrNull()
                ?: return@withContext false
            if (creds.uuid !in knownIds) return@withContext false
            store.save(LocalTunnel(
                id = creds.uuid,
                tariff = "armor",
                blob = ConfigParsers.serialize(creds),
                createdAt = System.currentTimeMillis()
            ))
            return@withContext true
        }

        // stainless: vpn://import/<base64>?name=...
        val ini = when {
            text.startsWith("vpn://import/") -> {
                val b64 = text.removePrefix("vpn://import/").substringBefore("?")
                runCatching {
                    String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                }.getOrNull() ?: return@withContext false
            }
            text.contains("[Interface]") -> text
            else -> {
                // пробуем как base64 — бот присылает конфиг именно так
                val decoded = runCatching {
                    String(android.util.Base64.decode(text, android.util.Base64.DEFAULT))
                }.getOrNull()
                if (decoded != null && decoded.contains("[Interface]")) decoded
                else return@withContext false
            }
        }

        val creds = runCatching { ConfigParsers.parseAwg(ini) }.getOrNull()
            ?: return@withContext false
        if (creds.privateKey.isBlank()) return@withContext false

        val ip = creds.address.substringBefore("/")
        if (ip.isBlank() || ip !in knownIds) return@withContext false

        store.save(LocalTunnel(
            id = ip,
            tariff = if (creds.jc > 0) "stainless" else "light",
            blob = ConfigParsers.serialize(creds),
            createdAt = System.currentTimeMillis()
        ))
        true
    }
}