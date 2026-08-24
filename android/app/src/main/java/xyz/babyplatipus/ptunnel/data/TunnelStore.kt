package xyz.babyplatipus.ptunnel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальные туннели этого устройства.
 *
 * Переключаться можно ТОЛЬКО между ними: приватные ключи AWG
 * на сервере не хранятся, поэтому туннель, созданный на другом
 * устройстве, здесь не поднять — его видно лишь в списке с сервера.
 */
data class LocalTunnel(
    val id: String,          // ip для awg, uuid для xray — совпадает с тем, что в базе
    val tariff: String,      // stainless | armor | light
    val blob: String,        // сериализованные креды (ConfigParsers.serialize)
    val createdAt: Long
)

class TunnelStore(private val context: Context) {

    private val key = stringPreferencesKey("local_tunnels")

    suspend fun all(): List<LocalTunnel> {
        val raw = context.dataStore.data.map { it[key] }.first() ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                LocalTunnel(
                    id = o.getString("id"),
                    tariff = o.getString("tariff"),
                    blob = o.getString("blob"),
                    createdAt = o.optLong("createdAt")
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun save(tunnel: LocalTunnel) {
        val current = all().filterNot { it.id == tunnel.id }.toMutableList()
        current.add(tunnel)
        write(current)
    }

    suspend fun remove(id: String) {
        write(all().filterNot { it.id == id })
    }

    suspend fun byId(id: String): LocalTunnel? = all().firstOrNull { it.id == id }

    private suspend fun write(list: List<LocalTunnel>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject()
                .put("id", it.id)
                .put("tariff", it.tariff)
                .put("blob", it.blob)
                .put("createdAt", it.createdAt))
        }
        context.dataStore.edit { prefs -> prefs[key] = arr.toString() }
    }
}