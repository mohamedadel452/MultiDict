package com.example.multidict

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Domain model representing a saved favorite word and its offline personal note.
 */
data class FavoriteWordItem(
    val word: String,
    val note: String
)

/**
 * MVVM Repository layer unifying local persistence ([AppPreferences], [AppearancePrefs]),
 * dictionary metadata ([DictionaryRepository]), live OpenSearch autocomplete ([WiktionarySuggestionClient]),
 * and on-device machine learning ([SmartMlHelper]).
 */
class MultiDictRepository(private val context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs = appContext.getSharedPreferences("MultiDictPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_WORD = "last_word"
        private const val KEY_LAST_DICT_INDEX = "last_dict_index"
    }

    // --- Active Search State Persistence ---

    fun getLastSearchedWord(): String {
        return prefs.getString(KEY_LAST_WORD, "") ?: ""
    }

    fun getLastSelectedDictIndex(): Int {
        return prefs.getInt(KEY_LAST_DICT_INDEX, 0)
    }

    fun saveLastSearchState(word: String, dictIndex: Int) {
        prefs.edit()
            .putString(KEY_LAST_WORD, word)
            .putInt(KEY_LAST_DICT_INDEX, dictIndex)
            .apply()
    }

    // --- Dictionaries ---

    fun getEnabledDictionaries(): List<Dictionary> {
        return AppPreferences.getOrderedDictionaries(appContext)
    }

    fun getAllOrderedDictionaries(): List<Dictionary> {
        return AppPreferences.getAllOrderedDictionaries(appContext)
    }

    fun isDictionaryEnabled(name: String): Boolean {
        return AppPreferences.isDictionaryEnabled(appContext, name)
    }

    fun setDictionaryEnabled(name: String, enabled: Boolean) {
        AppPreferences.setDictionaryEnabled(appContext, name, enabled)
    }

    fun saveDictionaryOrder(ordered: List<Dictionary>) {
        AppPreferences.saveDictionaryOrder(appContext, ordered)
    }

    fun addCustomDictionary(name: String, urlTemplate: String): Dictionary {
        val created = AppPreferences.addCustomDictionary(appContext, name, urlTemplate)
        AppPreferences.setDictionaryEnabled(appContext, created.name, true)
        return created
    }

    // --- Search History ---

    fun getHistory(): List<String> {
        return AppPreferences.getHistory(appContext)
    }

    fun addHistoryWord(word: String) {
        AppPreferences.addHistoryWord(appContext, word)
    }

    fun clearHistory() {
        AppPreferences.clearHistory(appContext)
    }

    // --- Favorites & Offline Notes ---

    fun getFavoritesWithNotes(): List<FavoriteWordItem> {
        return AppPreferences.getFavorites(appContext).map { word ->
            FavoriteWordItem(
                word = word,
                note = AppPreferences.getWordNote(appContext, word)
            )
        }
    }

    fun isFavorite(word: String): Boolean {
        return word.isNotBlank() && AppPreferences.isFavorite(appContext, word)
    }

    fun toggleFavorite(word: String): Boolean {
        return AppPreferences.toggleFavorite(appContext, word)
    }

    fun removeFavorite(word: String) {
        AppPreferences.removeFavorite(appContext, word)
    }

    fun getWordNote(word: String): String {
        return AppPreferences.getWordNote(appContext, word)
    }

    fun setWordNote(word: String, note: String) {
        AppPreferences.setWordNote(appContext, word, note)
    }

    // --- Live Wiktionary OpenSearch Suggestions ---

    suspend fun fetchSuggestions(prefix: String, limit: Int = 5): List<String> =
        withContext(Dispatchers.IO) {
            WiktionarySuggestionClient.fetchSuggestions(prefix).take(limit)
        }

    // --- On-Device Machine Learning (ML Kit) ---

    suspend fun identifyLanguage(text: String): String {
        return SmartMlHelper.identifyLanguage(text) ?: AppPreferences.ML_LANG_DE
    }

    suspend fun arePairModelsDownloaded(sourceLang: String, targetLang: String): Boolean {
        return SmartMlHelper.arePairModelsDownloaded(sourceLang, targetLang)
    }

    suspend fun translateOnDevice(text: String, sourceLang: String, targetLang: String): String? {
        return SmartMlHelper.translateOnDevice(text, sourceLang, targetLang)
    }

    suspend fun recognizeTextFromUri(imageUri: Uri): OcrScanResult? {
        return SmartMlHelper.recognizeTextFromUri(appContext, imageUri)
    }

    suspend fun isModelDownloaded(langCode: String): Boolean {
        return SmartMlHelper.isModelDownloaded(langCode)
    }

    suspend fun downloadLanguageModel(langCode: String, preferWifi: Boolean = false): Boolean {
        return SmartMlHelper.downloadLanguageModel(langCode, preferWifi)
    }

    suspend fun downloadPairModels(
        sourceLang: String,
        targetLang: String,
        preferWifi: Boolean = false
    ): Boolean {
        return SmartMlHelper.downloadPairModels(sourceLang, targetLang, preferWifi)
    }

    suspend fun deleteLanguageModel(langCode: String): Boolean {
        return SmartMlHelper.deleteLanguageModel(langCode)
    }

    // --- Settings & Preferences ---

    fun isSmartLanguageDetectionEnabled(): Boolean =
        AppPreferences.isSmartLanguageDetectionEnabled(appContext)

    fun isQuickTranslationEnabled(): Boolean =
        AppPreferences.isQuickTranslationEnabled(appContext)

    fun isQuickTranslationExpanded(): Boolean =
        AppPreferences.isQuickTranslationExpanded(appContext)

    fun setQuickTranslationExpanded(expanded: Boolean) =
        AppPreferences.setQuickTranslationExpanded(appContext, expanded)

    fun getTranslationTargetLanguage(): String =
        AppPreferences.getTranslationTargetLanguage(appContext)
}
