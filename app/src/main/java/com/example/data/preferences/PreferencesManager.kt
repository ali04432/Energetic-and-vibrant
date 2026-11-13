package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyPulseDataStore by preferencesDataStore(name = "studypulse_prefs")

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val focusMinutes: Int = 25,
  val shortBreakMinutes: Int = 5,
  val longBreakMinutes: Int = 15,
  val defaultReminderLeadMinutes: Long = 60L,
)

class PreferencesManager(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val FOCUS_MINUTES = intPreferencesKey("focus_minutes")
    val SHORT_BREAK_MINUTES = intPreferencesKey("short_break_minutes")
    val LONG_BREAK_MINUTES = intPreferencesKey("long_break_minutes")
    val REMINDER_LEAD_MINUTES = intPreferencesKey("reminder_lead_minutes")
  }

  val preferencesFlow: Flow<UserPreferences> =
    context.studyPulseDataStore.data.map { prefs ->
      val modeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
      val themeMode =
        ThemeMode.entries.firstOrNull { it.name == modeName } ?: ThemeMode.SYSTEM
      UserPreferences(
        themeMode = themeMode,
        focusMinutes = (prefs[Keys.FOCUS_MINUTES] ?: 25).coerceIn(5, 120),
        shortBreakMinutes = (prefs[Keys.SHORT_BREAK_MINUTES] ?: 5).coerceIn(1, 30),
        longBreakMinutes = (prefs[Keys.LONG_BREAK_MINUTES] ?: 15).coerceIn(5, 60),
        defaultReminderLeadMinutes = (prefs[Keys.REMINDER_LEAD_MINUTES] ?: 60).toLong(),
      )
    }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.studyPulseDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setPomodoroDurations(focus: Int, shortBreak: Int, longBreak: Int) {
    context.studyPulseDataStore.edit {
      it[Keys.FOCUS_MINUTES] = focus.coerceIn(5, 120)
      it[Keys.SHORT_BREAK_MINUTES] = shortBreak.coerceIn(1, 30)
      it[Keys.LONG_BREAK_MINUTES] = longBreak.coerceIn(5, 60)
    }
  }

  suspend fun setDefaultReminderLeadMinutes(minutes: Long) {
    context.studyPulseDataStore.edit {
      it[Keys.REMINDER_LEAD_MINUTES] = minutes.toInt().coerceIn(0, 10080)
    }
  }
}
