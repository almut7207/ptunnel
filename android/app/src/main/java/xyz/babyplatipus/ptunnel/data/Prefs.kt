package xyz.babyplatipus.ptunnel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
//import java.util.UUID

//private val Context.dataStore by preferencesDataStore(name = "ptunnel")

/**
 * Локальное хранилище. Ключи живут здесь и только здесь —
 * приложение получило их один раз и дальше менеджит само.
 */
class Prefs(private val context: Context) {

    private object Keys {
        //val DEVICE_ID = stringPreferencesKey("device_id")
        val PHONE = stringPreferencesKey("phone")
        val USERNAME = stringPreferencesKey("username")
        val TG_ID = stringPreferencesKey("tg_id")
        val CREDENTIALS = stringPreferencesKey("credentials")
        val TARIFF = stringPreferencesKey("tariff")
        val TG_LINKED = stringPreferencesKey("tg_linked")
        val SPLIT_EXCLUDED = stringSetPreferencesKey("split_excluded")
        val AUTOCONNECT = stringPreferencesKey("autoconnect")
        val BYPASS_OFFERED = stringPreferencesKey("bypass_offered")
        val IMPORT_OFFERED = stringPreferencesKey("import_offered")
    }

    /**
     * device_id генерится один раз при первом запуске и больше
     * не меняется. На бэке он превращается в username dev_<device_id>,
     * ровно как phone_<номер>.
     */
    /*suspend fun deviceId(): String {
        val existing = context.dataStore.data.map { it[Keys.DEVICE_ID] }.first()
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString().replace("-", "")
        context.dataStore.edit { it[Keys.DEVICE_ID] = fresh }
        return fresh
    }*/

    suspend fun phone(): String? =
        context.dataStore.data.map { it[Keys.PHONE] }.first()

    suspend fun needLogin(): Boolean =
        username().isNullOrBlank() && phone().isNullOrBlank()

    suspend fun savePhone(phone: String) {
        context.dataStore.edit {
            it[Keys.PHONE] = phone
            it[Keys.USERNAME] = "phone_$phone"
        }
    }

    suspend fun importOffered(): Boolean =
        context.dataStore.data.map { it[Keys.IMPORT_OFFERED] }.first() == "1"

    suspend fun markImportOffered() {
        context.dataStore.edit { it[Keys.IMPORT_OFFERED] = "1" }
    }

    suspend fun bypassOffered(): Boolean =
        context.dataStore.data.map { it[Keys.BYPASS_OFFERED] }.first() == "1"

    suspend fun markBypassOffered() {
        context.dataStore.edit { it[Keys.BYPASS_OFFERED] = "1" }
    }

    suspend fun autoConnect(): Boolean =
        context.dataStore.data.map { it[Keys.AUTOCONNECT] }.first() != "0"

    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTOCONNECT] = if (enabled) "1" else "0" }
    }

    /** Под каким username ходить в create_tunnel. */
    suspend fun username(): String? =
        context.dataStore.data.map { it[Keys.USERNAME] }.first()

    suspend fun tgId(): String? =
        context.dataStore.data.map { it[Keys.TG_ID] }.first()

    /** Вызывается после успешной привязки: username сменился на реальный. */
    suspend fun saveLinked(tgId: String, username: String) {
        context.dataStore.edit {
            it[Keys.TG_ID] = tgId
            it[Keys.USERNAME] = username
            it[Keys.TG_LINKED] = "1"
        }
    }

    val credentialsFlow: Flow<String?> =
        context.dataStore.data.map { it[Keys.CREDENTIALS] }

    suspend fun saveCredentials(blob: String, tariff: String) {
        context.dataStore.edit {
            it[Keys.CREDENTIALS] = blob
            it[Keys.TARIFF] = tariff
        }
    }

    suspend fun credentials(): String? =
        context.dataStore.data.map { it[Keys.CREDENTIALS] }.first()

    suspend fun tariff(): String? =
        context.dataStore.data.map { it[Keys.TARIFF] }.first()

    suspend fun clearCredentials() {
        context.dataStore.edit {
            it.remove(Keys.CREDENTIALS)
            it.remove(Keys.TARIFF)
        }
    }

    suspend fun isTelegramLinked(): Boolean =
        context.dataStore.data.map { it[Keys.TG_LINKED] }.first() == "1"

    suspend fun markTelegramLinked() {
        context.dataStore.edit { it[Keys.TG_LINKED] = "1" }
    }

    // --- split-tunneling: пакеты, которые идут МИМО туннеля ---

    val splitExcludedFlow: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SPLIT_EXCLUDED] ?: emptySet() }

    suspend fun splitExcluded(): Set<String> =
        context.dataStore.data.map { it[Keys.SPLIT_EXCLUDED] ?: emptySet() }.first()

    suspend fun setSplitExcluded(packages: Set<String>) {
        context.dataStore.edit { it[Keys.SPLIT_EXCLUDED] = packages }
    }
}
