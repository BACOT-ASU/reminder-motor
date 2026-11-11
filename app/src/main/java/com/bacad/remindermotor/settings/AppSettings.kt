package com.bacad.remindermotor.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Pengaturan sederhana yang disimpan di SharedPreferences. */
object AppSettings {
    private const val PREFS = "app_settings"
    private const val KEY_LANGUAGE_CHOSEN = "language_chosen"
    private const val KEY_NOTIF_HOUR = "notif_hour"
    private const val KEY_NOTIF_MINUTE = "notif_minute"

    const val LANG_ID = "id"
    const val LANG_EN = "en"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Bahasa ----
    fun isLanguageChosen(context: Context) = prefs(context).getBoolean(KEY_LANGUAGE_CHOSEN, false)

    /** Bahasa yang sedang dipakai aplikasi: "id" atau "en". */
    fun currentLanguage(): String {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val lang = if (tag.isBlank()) java.util.Locale.getDefault().language else tag.substringBefore(',').substringBefore('-')
        return if (lang == "in" || lang == "id") LANG_ID else LANG_EN
    }

    /** Ganti bahasa aplikasi. Layar akan dimuat ulang otomatis oleh sistem. */
    fun setLanguage(context: Context, lang: String) {
        prefs(context).edit().putBoolean(KEY_LANGUAGE_CHOSEN, true).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }

    // ---- Jam notifikasi ----
    fun notifHour(context: Context) = prefs(context).getInt(KEY_NOTIF_HOUR, 7)
    fun notifMinute(context: Context) = prefs(context).getInt(KEY_NOTIF_MINUTE, 0)

    fun setNotifTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit().putInt(KEY_NOTIF_HOUR, hour).putInt(KEY_NOTIF_MINUTE, minute).apply()
    }
}
