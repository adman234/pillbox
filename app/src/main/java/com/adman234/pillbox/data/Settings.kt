package com.adman234.pillbox.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

const val SOUND_DEFAULT = "default"
const val SOUND_SILENT = "silent"

data class Settings(
    /** [SOUND_DEFAULT], [SOUND_SILENT] or a content uri string. */
    val notifSound: String = SOUND_DEFAULT,
    val notifVibrate: Boolean = true,
    val alarmSound: String = SOUND_DEFAULT,
    val alarmVibrate: Boolean = true,
    val autoRepeat: Boolean = false,
    val repeatMinutes: Int = 5,
    val snoozeMinutes: Int = 10,
    /** On: reminders keep their clock time across daylight saving changes. */
    val dstAdjust: Boolean = true,
    val showLastDose: Boolean = true,
    val onboarded: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

private object K {
    val notifSound = stringPreferencesKey("notif_sound")
    val notifVibrate = booleanPreferencesKey("notif_vibrate")
    val alarmSound = stringPreferencesKey("alarm_sound")
    val alarmVibrate = booleanPreferencesKey("alarm_vibrate")
    val autoRepeat = booleanPreferencesKey("auto_repeat")
    val repeatMinutes = intPreferencesKey("repeat_minutes")
    val snoozeMinutes = intPreferencesKey("snooze_minutes")
    val dstAdjust = booleanPreferencesKey("dst_adjust")
    val showLastDose = booleanPreferencesKey("show_last_dose")
    val onboarded = booleanPreferencesKey("onboarded")
}

private fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        notifSound = this[K.notifSound] ?: d.notifSound,
        notifVibrate = this[K.notifVibrate] ?: d.notifVibrate,
        alarmSound = this[K.alarmSound] ?: d.alarmSound,
        alarmVibrate = this[K.alarmVibrate] ?: d.alarmVibrate,
        autoRepeat = this[K.autoRepeat] ?: d.autoRepeat,
        repeatMinutes = this[K.repeatMinutes] ?: d.repeatMinutes,
        snoozeMinutes = this[K.snoozeMinutes] ?: d.snoozeMinutes,
        dstAdjust = this[K.dstAdjust] ?: d.dstAdjust,
        showLastDose = this[K.showLastDose] ?: d.showLastDose,
        onboarded = this[K.onboarded] ?: d.onboarded,
    )
}

class SettingsRepo(private val ctx: Context) {
    val flow: Flow<Settings> = ctx.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = flow.first()

    suspend fun update(block: (Settings) -> Settings) {
        ctx.dataStore.edit { p ->
            val s = block(p.toSettings())
            p[K.notifSound] = s.notifSound
            p[K.notifVibrate] = s.notifVibrate
            p[K.alarmSound] = s.alarmSound
            p[K.alarmVibrate] = s.alarmVibrate
            p[K.autoRepeat] = s.autoRepeat
            p[K.repeatMinutes] = s.repeatMinutes
            p[K.snoozeMinutes] = s.snoozeMinutes
            p[K.dstAdjust] = s.dstAdjust
            p[K.showLastDose] = s.showLastDose
            p[K.onboarded] = s.onboarded
        }
    }
}
