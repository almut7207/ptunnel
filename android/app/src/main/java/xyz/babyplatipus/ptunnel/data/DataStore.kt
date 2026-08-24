package xyz.babyplatipus.ptunnel.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Общее хранилище настроек — используется Prefs и TunnelStore. */
val Context.dataStore by preferencesDataStore(name = "ptunnel")