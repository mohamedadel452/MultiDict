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
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.color.DynamicColors
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages user settings ("Einstellungen"), dictionary order, enabled/disabled dictionaries,
 * custom dictionaries ("Wörterbücher"), search history (max 200), favorite words, and offline notes.
 */
object AppPreferences {

    private const val PREFS_NAME = "multidict_prefs"
    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_DYNAMIC_COLOR = "pref_dynamic_color"
    private const val KEY_AUTO_REMOVE_WORD = "pref_auto_remove_word"
    private const val KEY_VIBRATE_ON_CLICK = "pref_vibrate_on_click"
    private const val KEY_DESKTOP_MODE = "pref_desktop_mode"
    private const val KEY_AUTO_SEARCH_TABS = "pref_auto_search_tabs"
    private const val KEY_DICTIONARY_ORDER = "pref_dictionary_order"
    private const val KEY_DISABLED_DICTIONARIES = "pref_disabled_dictionaries_json"
    private const val KEY_CUSTOM_DICTIONARIES = "pref_custom_dictionaries_json"
    private const val KEY_HISTORY_JSON = "pref_history_json"
    private const val KEY_FAVORITES_JSON = "pref_favorites_json"
    private const val KEY_WORD_NOTES_JSON = "pref_word_notes_json"
    private const val KEY_VOICE_LANG = "pref_voice_lang"
    private const val KEY_VOICE_AUTO_SEARCH = "pref_voice_auto_search"
    private const val KEY_SMART_LANG_DETECT = "pref_smart_lang_detect"
    private const val KEY_QUICK_TRANSLATE_ENABLED = "pref_quick_translate_enabled"
    private const val KEY_QUICK_TRANSLATE_EXPANDED = "pref_quick_translate_expanded"
    private const val KEY_TRANSLATE_TARGET_LANG = "pref_translate_target_lang"
    private const val MAX_HISTORY_SIZE = 200

    const val VOICE_LANG_DE = "de-DE"
    const val VOICE_LANG_EN = "en-US"
    const val VOICE_LANG_AR = "ar-SA"

    const val ML_LANG_DE = "de"
    const val ML_LANG_EN = "en"
    const val ML_LANG_AR = "ar"

    fun getThemeMode(context: Context): Int = AppearancePrefs.getThemeMode(context)

    fun setThemeMode(context: Context, mode: Int) = AppearancePrefs.setThemeMode(context, mode)

    fun applySavedTheme(context: Context) {
        if (context is Activity) {
            AppearancePrefs.applyToActivity(context)
        } else {
            AppearancePrefs.applyOnStartup(context)
        }
    }

    fun isDynamicColorEnabled(context: Context): Boolean = AppearancePrefs.isDynamicColorEnabled(context)

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) =
        AppearancePrefs.setDynamicColorEnabled(context, enabled)

    /**
     * Checks if system animations are enabled (respects Accessibility "Remove animations").
     */
    fun areAnimationsEnabled(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale > 0f
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Gentle staggered fade + slide-up entrance animation for the first 8 items in a RecyclerView.
     */
    fun animateListItem(view: View, position: Int) {
        if (position >= 8 || !areAnimationsEnabled(view.context)) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        val offsetPx = 10f * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = offsetPx
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay((position * 25L).coerceAtMost(175L))
            .setDuration(220L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    fun isAutoRemoveWordEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_REMOVE_WORD, true)
    }

    fun setAutoRemoveWordEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_REMOVE_WORD, enabled)
            .apply()
    }

    fun isVibrateOnClickEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATE_ON_CLICK, true)
    }

    fun setVibrateOnClickEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VIBRATE_ON_CLICK, enabled)
            .apply()
    }

    fun isDesktopModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DESKTOP_MODE, false)
    }

    fun setDesktopModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DESKTOP_MODE, enabled)
            .apply()
    }

    fun isAutoSearchTabsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SEARCH_TABS, true)
    }

    fun setAutoSearchTabsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_SEARCH_TABS, enabled)
            .apply()
    }

    // --- Phase 9: Voice Search, Smart Language Detection & On-Device Translation Prefs ---

    fun getVoiceLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VOICE_LANG, VOICE_LANG_DE) ?: VOICE_LANG_DE
    }

    fun setVoiceLanguage(context: Context, langTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VOICE_LANG, langTag)
            .apply()
    }

    fun getVoiceLanguageShortCode(context: Context): String {
        return when (getVoiceLanguage(context)) {
            VOICE_LANG_EN -> "EN"
            VOICE_LANG_AR -> "AR"
            else -> "DE"
        }
    }

    fun isVoiceAutoSearchEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOICE_AUTO_SEARCH, true)
    }

    fun setVoiceAutoSearchEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VOICE_AUTO_SEARCH, enabled)
            .apply()
    }

    fun isSmartLanguageDetectionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SMART_LANG_DETECT, true)
    }

    fun setSmartLanguageDetectionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SMART_LANG_DETECT, enabled)
            .apply()
    }

    fun isQuickTranslationEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_QUICK_TRANSLATE_ENABLED, true)
    }

    fun setQuickTranslationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_QUICK_TRANSLATE_ENABLED, enabled)
            .apply()
    }

    fun isQuickTranslationExpanded(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_QUICK_TRANSLATE_EXPANDED, true)
    }

    fun setQuickTranslationExpanded(context: Context, expanded: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_QUICK_TRANSLATE_EXPANDED, expanded)
            .apply()
    }

    fun getTranslationTargetLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TRANSLATE_TARGET_LANG, ML_LANG_AR) ?: ML_LANG_AR
    }

    fun setTranslationTargetLanguage(context: Context, langCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRANSLATE_TARGET_LANG, langCode)
            .apply()
    }

    fun getMlLanguageDisplayName(context: Context, langCode: String): String {
        return when (langCode.lowercase()) {
            ML_LANG_AR -> context.getString(R.string.lang_name_ar)
            ML_LANG_EN -> context.getString(R.string.lang_name_en)
            else -> context.getString(R.string.lang_name_de)
        }
    }

    /**
     * Triggers a short click vibration if "Vibrieren auf Klick" is enabled in Einstellungen.
     */
    fun vibrateIfEnabled(view: View) {
        val context = view.context
        if (!isVibrateOnClickEnabled(context)) return

        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(20L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(20L)
                }
            }
        } catch (_: Exception) {
            // Ignore vibration errors on unsupported devices
        }
    }

    // --- Custom Dictionaries ---

    fun getCustomDictionaries(context: Context): MutableList<Dictionary> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_DICTIONARIES, null)
        if (raw.isNullOrBlank()) return mutableListOf()

        val list = mutableListOf<Dictionary>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                val shortName = obj.optString("shortName").trim()
                val searchUrl = obj.optString("searchUrl").trim()
                val homeUrl = obj.optString("homeUrl").trim()
                if (name.isNotEmpty() && searchUrl.contains("{q}")) {
                    list.add(Dictionary(name, shortName.ifEmpty { deriveShortName(name) }, searchUrl, homeUrl))
                }
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }
        return list
    }

    fun addCustomDictionary(context: Context, name: String, searchUrl: String): Dictionary {
        val trimmedName = name.trim()
        val trimmedUrl = searchUrl.trim()
        val shortName = deriveShortName(trimmedName)
        val homeUrl = deriveHomeUrl(trimmedUrl)
        val newDict = Dictionary(trimmedName, shortName, trimmedUrl, homeUrl)

        val customList = getCustomDictionaries(context)
        customList.removeAll { it.name.equals(trimmedName, ignoreCase = true) }
        customList.add(newDict)

        val array = JSONArray()
        for (dict in customList) {
            val obj = JSONObject().apply {
                put("name", dict.name)
                put("shortName", dict.shortName)
                put("searchUrl", dict.searchUrl)
                put("homeUrl", dict.homeUrl)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_DICTIONARIES, array.toString())
            .apply()

        return newDict
    }

    private fun deriveShortName(name: String): String {
        val letters = name.trim().filter { it.isLetterOrDigit() }
        return letters.take(2).uppercase().ifEmpty { "CD" }
    }

    private fun deriveHomeUrl(searchUrl: String): String {
        return try {
            val uri = Uri.parse(searchUrl.replace("{q}", ""))
            val scheme = uri.scheme ?: "https"
            val host = uri.host
            if (!host.isNullOrEmpty()) "$scheme://$host/" else searchUrl.replace("{q}", "")
        } catch (_: Exception) {
            searchUrl.replace("{q}", "")
        }
    }

    // --- Enabled / Disabled Dictionaries ---

    fun getDisabledDictionaryNames(context: Context): MutableSet<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISABLED_DICTIONARIES, null)
        return parseJsonStringList(raw).toMutableSet()
    }

    fun isDictionaryEnabled(context: Context, name: String): Boolean {
        return !getDisabledDictionaryNames(context).contains(name)
    }

    fun setDictionaryEnabled(context: Context, name: String, enabled: Boolean) {
        val disabled = getDisabledDictionaryNames(context)
        if (enabled) {
            disabled.remove(name)
        } else {
            disabled.add(name)
        }
        saveJsonStringList(context, KEY_DISABLED_DICTIONARIES, disabled.toList())
    }

    fun getAllOrderedDictionaries(context: Context): MutableList<Dictionary> {
        val allAvailable = DictionaryRepository.dictionaries + getCustomDictionaries(context)
        val savedOrderCsv = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DICTIONARY_ORDER, null)

        if (savedOrderCsv.isNullOrBlank()) {
            return allAvailable.toMutableList()
        }

        val byName = allAvailable.associateBy { it.name }.toMutableMap()
        val ordered = mutableListOf<Dictionary>()
        for (name in savedOrderCsv.split("|")) {
            val dict = byName.remove(name)
            if (dict != null) {
                ordered.add(dict)
            }
        }
        ordered.addAll(byName.values)
        return ordered
    }

    fun getOrderedDictionaries(context: Context): MutableList<Dictionary> {
        val allOrdered = getAllOrderedDictionaries(context)
        val disabled = getDisabledDictionaryNames(context)
        val enabledList = allOrdered.filter { !disabled.contains(it.name) }.toMutableList()
        return if (enabledList.isNotEmpty()) enabledList else mutableListOf(allOrdered.first())
    }

    fun saveDictionaryOrder(context: Context, orderedList: List<Dictionary>) {
        val csv = orderedList.joinToString("|") { it.name }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DICTIONARY_ORDER, csv)
            .apply()
    }

    // --- Search History (newest first, no duplicates, max 200) ---

    fun getHistory(context: Context): MutableList<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY_JSON, null)
        return parseJsonStringList(raw)
    }

    fun addHistoryWord(context: Context, word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return

        val history = getHistory(context)
        history.removeAll { it.equals(trimmed, ignoreCase = true) }
        history.add(0, trimmed)
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
        saveJsonStringList(context, KEY_HISTORY_JSON, history)
    }

    fun clearHistory(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY_JSON)
            .apply()
    }

    // --- Favorites & Offline Personal Notes ---

    fun getFavorites(context: Context): MutableList<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FAVORITES_JSON, null)
        return parseJsonStringList(raw)
    }

    fun isFavorite(context: Context, word: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return false
        return getFavorites(context).any { it.equals(trimmed, ignoreCase = true) }
    }

    fun toggleFavorite(context: Context, word: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return false

        val favorites = getFavorites(context)
        val existingIdx = favorites.indexOfFirst { it.equals(trimmed, ignoreCase = true) }
        return if (existingIdx >= 0) {
            favorites.removeAt(existingIdx)
            saveJsonStringList(context, KEY_FAVORITES_JSON, favorites)
            false
        } else {
            favorites.add(0, trimmed)
            saveJsonStringList(context, KEY_FAVORITES_JSON, favorites)
            true
        }
    }

    fun removeFavorite(context: Context, word: String) {
        val trimmed = word.trim()
        val favorites = getFavorites(context)
        if (favorites.removeAll { it.equals(trimmed, ignoreCase = true) }) {
            saveJsonStringList(context, KEY_FAVORITES_JSON, favorites)
        }
    }

    /**
     * Retrieves the offline personal note / meaning attached to a favorite [word].
     */
    fun getWordNote(context: Context, word: String): String {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return ""
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WORD_NOTES_JSON, null) ?: return ""
        return try {
            JSONObject(raw).optString(key, "")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Saves an offline personal note / meaning for [word].
     */
    fun setWordNote(context: Context, word: String, note: String) {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_WORD_NOTES_JSON, null)
        val obj = try {
            if (!raw.isNullOrBlank()) JSONObject(raw) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }

        val trimmedNote = note.trim()
        if (trimmedNote.isEmpty()) {
            obj.remove(key)
        } else {
            obj.put(key, trimmedNote)
        }
        prefs.edit().putString(KEY_WORD_NOTES_JSON, obj.toString()).apply()
    }

    private fun parseJsonStringList(rawJson: String?): MutableList<String> {
        if (rawJson.isNullOrBlank()) return mutableListOf()
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val item = array.optString(i).trim()
                if (item.isNotEmpty()) {
                    list.add(item)
                }
            }
        } catch (_: Exception) {
            // Ignore malformed JSON
        }
        return list
    }

    private fun saveJsonStringList(context: Context, key: String, list: List<String>) {
        val array = JSONArray()
        for (item in list) {
            array.put(item)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, array.toString())
            .apply()
    }
}
