package com.example.multidict.data

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.DynamicColors

/**
 * Phase 8: Central helper class for manual Language ("System default", "English", "العربية")
 * and Theme ("System default", "Light", "Dark", plus optional Dynamic Color) selection.
 */
object AppearancePrefs {

    private const val PREFS_NAME = "multidict_prefs"
    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_LANGUAGE_TAG = "pref_language_tag"
    private const val KEY_DYNAMIC_COLOR = "pref_dynamic_color"

    const val LANG_SYSTEM = ""
    const val LANG_EN = "en"
    const val LANG_AR = "ar"

    /**
     * Called in [MultiDictApp.onCreate] BEFORE any activity is created so there is zero flicker.
     */
    fun applyOnStartup(context: Context) {
        val savedMode = getThemeMode(context)
        AppCompatDelegate.setDefaultNightMode(savedMode)
    }

    /**
     * Applies saved theme mode, optional Material You dynamic color, and status/navigation bar icon contrast.
     */
    fun applyToActivity(activity: Activity) {
        AppCompatDelegate.setDefaultNightMode(getThemeMode(activity))
        if (isDynamicColorEnabled(activity)) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    /**
     * Updates status bar and navigation bar icon appearance (dark icons on light background, light icons on dark).
     */
    fun syncSystemBarIconColors(activity: Activity) {
        val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !isNight
        controller.isAppearanceLightNavigationBars = !isNight
    }

    // --- Theme (System default / Light / Dark) ---

    fun getThemeMode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    fun setThemeMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THEME_MODE, mode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getThemeDisplayLabel(context: Context): String {
        return when (getThemeMode(context)) {
            AppCompatDelegate.MODE_NIGHT_NO -> context.getString(R.string.theme_light)
            AppCompatDelegate.MODE_NIGHT_YES -> context.getString(R.string.theme_dark)
            else -> context.getString(R.string.theme_system)
        }
    }

    // --- Language (System default / English / العربية) ---

    /**
     * Returns "", "en", or "ar" based on current per-app locale or saved preference.
     */
    fun getLanguageTag(context: Context): String {
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        if (!currentAppLocales.isEmpty) {
            val primaryLang = currentAppLocales[0]?.language?.lowercase().orEmpty()
            if (primaryLang.startsWith("ar")) return LANG_AR
            if (primaryLang.startsWith("en")) return LANG_EN
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, LANG_SYSTEM)
            .orEmpty()
    }

    /**
     * Persists and immediately applies the selected language tag ("", "en", or "ar")
     * using [AppCompatDelegate.setApplicationLocales].
     */
    fun setLanguageTag(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()

        val localeList = if (languageTag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getLanguageDisplayLabel(context: Context): String {
        return when (getLanguageTag(context)) {
            LANG_EN -> context.getString(R.string.lang_english)
            LANG_AR -> context.getString(R.string.lang_arabic)
            else -> context.getString(R.string.lang_system)
        }
    }

    // --- Optional Android 12+ Material You Dynamic Color ---

    fun isDynamicColorEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DYNAMIC_COLOR, enabled)
            .apply()
    }
}
