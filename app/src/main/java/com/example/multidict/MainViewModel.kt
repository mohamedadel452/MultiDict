package com.example.multidict

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable UI state for Smart Language Detection & Collapsible Instant On-Device Translation (Phase 9).
 */
data class SmartMlUiState(
    val detectedLangCode: String = AppPreferences.ML_LANG_DE,
    val showDetectedBanner: Boolean = false,
    val suggestedDictIndex: Int? = null,
    val suggestedDictName: String? = null,
    val showQuickTranslationCard: Boolean = false,
    val isQuickTranslationExpanded: Boolean = true,
    val sourceLangCode: String = AppPreferences.ML_LANG_DE,
    val targetLangCode: String = AppPreferences.ML_LANG_AR,
    val modelsReady: Boolean = false,
    val isTranslating: Boolean = false,
    val translatedText: String = ""
)

/**
 * Immutable UI state for the 3D Flashcards study screen.
 */
data class FlashcardUiState(
    val isEmpty: Boolean = true,
    val currentWord: String = "",
    val currentNote: String = "",
    val currentCardIndex: Int = 1,
    val totalCardsCount: Int = 0,
    val knownCount: Int = 0,
    val isShowingBack: Boolean = false
)

/**
 * Immutable UI state for the Camera / Gallery OCR bottom sheet.
 */
data class OcrUiState(
    val isLoading: Boolean = false,
    val scanResult: OcrScanResult? = null,
    val hasError: Boolean = false,
    val isPhraseMode: Boolean = false,
    val selectedPhraseWords: Set<String> = emptySet()
)

/**
 * Shared MVVM [AndroidViewModel] managing the state and business logic for [MainActivity]
 * and its child fragments ([HistoryFragment], [FavoritesFragment], [FlashcardsFragment],
 * [DictionariesOrderFragment], [SettingsFragment], and [OcrScanDialogFragment]).
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository = MultiDictRepository(application)

    override fun onCleared() {
        viewModelScope.cancel()
        super.onCleared()
    }

    // --- 1. Active Word & Selected Dictionary Tab ---

    private val _dictionaries = MutableStateFlow(repository.getEnabledDictionaries())
    val dictionaries: StateFlow<List<Dictionary>> = _dictionaries.asStateFlow()

    private val _allOrderedDictionaries = MutableStateFlow(repository.getAllOrderedDictionaries())
    val allOrderedDictionaries: StateFlow<List<Dictionary>> = _allOrderedDictionaries.asStateFlow()

    private val _currentWord = MutableStateFlow(repository.getLastSearchedWord())
    val currentWord: StateFlow<String> = _currentWord.asStateFlow()

    private val _selectedDictIndex = MutableStateFlow(
        repository.getLastSelectedDictIndex()
            .coerceIn(0, (_dictionaries.value.size - 1).coerceAtLeast(0))
    )
    val selectedDictIndex: StateFlow<Int> = _selectedDictIndex.asStateFlow()

    // --- 2. History & Favorites ---

    private val _historyList = MutableStateFlow(repository.getHistory())
    val historyList: StateFlow<List<String>> = _historyList.asStateFlow()

    private val _favoritesList = MutableStateFlow(repository.getFavoritesWithNotes())
    val favoritesList: StateFlow<List<FavoriteWordItem>> = _favoritesList.asStateFlow()

    private val _isCurrentWordFavorite = MutableStateFlow(repository.isFavorite(_currentWord.value))
    val isCurrentWordFavorite: StateFlow<Boolean> = _isCurrentWordFavorite.asStateFlow()

    // --- 3. Live Autocomplete Suggestions ---

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    private var suggestionJob: Job? = null

    // --- 4. Smart Language Detection & Quick On-Device Translation ---

    private val _smartMlState = MutableStateFlow(
        SmartMlUiState(isQuickTranslationExpanded = repository.isQuickTranslationExpanded())
    )
    val smartMlState: StateFlow<SmartMlUiState> = _smartMlState.asStateFlow()
    private var smartMlJob: Job? = null
    private var dismissedDetectedWord: String = ""

    // --- 5. Flashcards Study Deck ---

    private val flashcardDeck = ArrayDeque<String>()
    private val _flashcardState = MutableStateFlow(FlashcardUiState())
    val flashcardState: StateFlow<FlashcardUiState> = _flashcardState.asStateFlow()

    // --- 6. Camera / Image OCR State ---

    private val _ocrState = MutableStateFlow(OcrUiState())
    val ocrState: StateFlow<OcrUiState> = _ocrState.asStateFlow()
    private val selectedOcrWordsSet = LinkedHashSet<String>()

    // =========================================================================
    // Search & Dictionary Tab Actions
    // =========================================================================

    fun restoreOrUpdateSearchState(word: String, dictIndex: Int) {
        val cleanWord = word.trim()
        val coercedIndex = dictIndex.coerceIn(0, (_dictionaries.value.size - 1).coerceAtLeast(0))
        _currentWord.value = cleanWord
        _selectedDictIndex.value = coercedIndex
        _isCurrentWordFavorite.value = repository.isFavorite(cleanWord)
        repository.saveLastSearchState(cleanWord, coercedIndex)
    }

    fun submitSearchWord(word: String, allowAutoSwitchTab: Boolean = true) {
        val cleanWord = word.trim()
        if (cleanWord.isNotEmpty()) {
            _currentWord.value = cleanWord
            repository.addHistoryWord(cleanWord)
            _historyList.value = repository.getHistory()
        }
        _isCurrentWordFavorite.value = repository.isFavorite(_currentWord.value)
        repository.saveLastSearchState(_currentWord.value, _selectedDictIndex.value)

        if (_currentWord.value.isNotEmpty()) {
            analyzeSmartMl(_currentWord.value, allowAutoSwitchTab = allowAutoSwitchTab)
        }
    }

    fun selectDictionaryTab(index: Int) {
        val list = _dictionaries.value
        if (index !in list.indices) return
        _selectedDictIndex.value = index
        repository.saveLastSearchState(_currentWord.value, index)
    }

    fun refreshDictionariesFromStorage() {
        val currentDictName = _dictionaries.value.getOrNull(_selectedDictIndex.value)?.name
        val updatedEnabled = repository.getEnabledDictionaries()
        _dictionaries.value = updatedEnabled
        _allOrderedDictionaries.value = repository.getAllOrderedDictionaries()

        val newSelectedIdx = updatedEnabled.indexOfFirst { it.name == currentDictName }
            .takeIf { it >= 0 } ?: 0
        _selectedDictIndex.value = newSelectedIdx
        repository.saveLastSearchState(_currentWord.value, newSelectedIdx)
    }

    fun saveDictionaryOrder(orderedAll: List<Dictionary>) {
        repository.saveDictionaryOrder(orderedAll)
        refreshDictionariesFromStorage()
    }

    fun setDictionaryEnabled(name: String, enabled: Boolean) {
        repository.setDictionaryEnabled(name, enabled)
        refreshDictionariesFromStorage()
    }

    fun addCustomDictionary(name: String, urlTemplate: String): Dictionary {
        val created = repository.addCustomDictionary(name, urlTemplate)
        refreshDictionariesFromStorage()
        return created
    }

    // =========================================================================
    // Autocomplete Suggestions
    // =========================================================================

    fun requestSuggestions(prefix: String) {
        val clean = prefix.trim()
        suggestionJob?.cancel()
        if (clean.length < 2) {
            _suggestions.value = emptyList()
            return
        }
        suggestionJob = viewModelScope.launch {
            val results = repository.fetchSuggestions(clean, limit = 5)
            _suggestions.value = results
        }
    }

    fun clearSuggestions() {
        suggestionJob?.cancel()
        _suggestions.value = emptyList()
    }

    // =========================================================================
    // History & Favorites Actions
    // =========================================================================

    fun refreshHistory() {
        _historyList.value = repository.getHistory()
    }

    fun clearHistory() {
        repository.clearHistory()
        _historyList.value = emptyList()
    }

    fun refreshFavorites() {
        _favoritesList.value = repository.getFavoritesWithNotes()
        _isCurrentWordFavorite.value = repository.isFavorite(_currentWord.value)
    }

    fun toggleCurrentWordFavorite() {
        val word = _currentWord.value
        if (word.isBlank()) return
        val nowFav = repository.toggleFavorite(word)
        _isCurrentWordFavorite.value = nowFav
        _favoritesList.value = repository.getFavoritesWithNotes()
    }

    fun removeFavorite(word: String) {
        repository.removeFavorite(word)
        _favoritesList.value = repository.getFavoritesWithNotes()
        _isCurrentWordFavorite.value = repository.isFavorite(_currentWord.value)
    }

    fun updateWordNote(word: String, note: String) {
        repository.setWordNote(word, note)
        _favoritesList.value = repository.getFavoritesWithNotes()
    }

    // =========================================================================
    // Flashcards Deck Actions
    // =========================================================================

    fun initFlashcardDeck() {
        val favs = repository.getFavoritesWithNotes()
        _favoritesList.value = favs
        flashcardDeck.clear()
        flashcardDeck.addAll(favs.map { it.word })

        if (flashcardDeck.isEmpty()) {
            _flashcardState.value = FlashcardUiState(isEmpty = true)
            return
        }

        val firstWord = flashcardDeck.first()
        _flashcardState.value = FlashcardUiState(
            isEmpty = false,
            currentWord = firstWord,
            currentNote = repository.getWordNote(firstWord),
            currentCardIndex = 1,
            totalCardsCount = flashcardDeck.size,
            knownCount = 0,
            isShowingBack = false
        )
    }

    fun toggleFlashcardSide() {
        val current = _flashcardState.value
        if (current.isEmpty) return
        _flashcardState.value = current.copy(
            isShowingBack = !current.isShowingBack,
            currentNote = repository.getWordNote(current.currentWord)
        )
    }

    fun markFlashcardReviewAgain() {
        val currentWord = flashcardDeck.removeFirstOrNull() ?: return
        val insertPos = minOf(2, flashcardDeck.size)
        val tempList = flashcardDeck.toMutableList()
        tempList.add(insertPos, currentWord)
        flashcardDeck.clear()
        flashcardDeck.addAll(tempList)

        val nextWord = flashcardDeck.firstOrNull().orEmpty()
        _flashcardState.value = _flashcardState.value.copy(
            currentWord = nextWord,
            currentNote = repository.getWordNote(nextWord),
            isShowingBack = false
        )
    }

    fun markFlashcardKnown() {
        val currentWord = flashcardDeck.removeFirstOrNull() ?: return
        flashcardDeck.addLast(currentWord)
        val current = _flashcardState.value
        val total = current.totalCardsCount.coerceAtLeast(1)
        val nextWord = flashcardDeck.firstOrNull().orEmpty()

        _flashcardState.value = current.copy(
            currentWord = nextWord,
            currentNote = repository.getWordNote(nextWord),
            knownCount = current.knownCount + 1,
            currentCardIndex = (current.currentCardIndex % total) + 1,
            isShowingBack = false
        )
    }

    // =========================================================================
    // Smart Language Detection & Instant On-Device Translation (Phase 9)
    // =========================================================================

    fun analyzeSmartMl(word: String, allowAutoSwitchTab: Boolean) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) {
            _smartMlState.value = _smartMlState.value.copy(
                showDetectedBanner = false,
                showQuickTranslationCard = false
            )
            return
        }

        smartMlJob?.cancel()
        smartMlJob = viewModelScope.launch {
            val detectedLang = repository.identifyLanguage(trimmed)
            val dictList = _dictionaries.value
            val currentIdx = _selectedDictIndex.value

            var suggestedIdx: Int? = null
            var suggestedName: String? = null

            if (repository.isSmartLanguageDetectionEnabled()) {
                when (detectedLang) {
                    AppPreferences.ML_LANG_AR -> {
                        if (allowAutoSwitchTab) {
                            val arabicDictIdx = dictList.indexOfFirst {
                                it.name.equals("ArabDict", ignoreCase = true) ||
                                    it.name.equals("PONS", ignoreCase = true) ||
                                    it.name.equals("Langenscheidt", ignoreCase = true)
                            }
                            if (arabicDictIdx >= 0 && arabicDictIdx != currentIdx) {
                                selectDictionaryTab(arabicDictIdx)
                            }
                        }
                    }

                    AppPreferences.ML_LANG_EN -> {
                        val englishDictIdx = dictList.indexOfFirst {
                            it.name.equals("PONS", ignoreCase = true) ||
                                it.name.equals("FreeDic", ignoreCase = true) ||
                                it.name.equals("Langenscheidt", ignoreCase = true)
                        }
                        if (englishDictIdx >= 0 && englishDictIdx != currentIdx) {
                            suggestedIdx = englishDictIdx
                            suggestedName = dictList[englishDictIdx].name
                        }
                    }
                }
            }

            val showBanner = repository.isSmartLanguageDetectionEnabled() &&
                !trimmed.equals(dismissedDetectedWord, ignoreCase = true)

            val quickEnabled = repository.isQuickTranslationEnabled()
            val targetLang = resolveEffectiveTargetLanguage(detectedLang)

            if (!quickEnabled) {
                _smartMlState.value = _smartMlState.value.copy(
                    detectedLangCode = detectedLang,
                    showDetectedBanner = showBanner,
                    suggestedDictIndex = suggestedIdx,
                    suggestedDictName = suggestedName,
                    showQuickTranslationCard = false
                )
                return@launch
            }

            val modelsReady = repository.arePairModelsDownloaded(detectedLang, targetLang)
            _smartMlState.value = _smartMlState.value.copy(
                detectedLangCode = detectedLang,
                showDetectedBanner = showBanner,
                suggestedDictIndex = suggestedIdx,
                suggestedDictName = suggestedName,
                showQuickTranslationCard = true,
                isQuickTranslationExpanded = repository.isQuickTranslationExpanded(),
                sourceLangCode = detectedLang,
                targetLangCode = targetLang,
                modelsReady = modelsReady,
                isTranslating = modelsReady,
                translatedText = ""
            )

            if (modelsReady) {
                val translated = repository.translateOnDevice(trimmed, detectedLang, targetLang)
                _smartMlState.value = _smartMlState.value.copy(
                    isTranslating = false,
                    translatedText = if (!translated.isNullOrBlank()) translated else trimmed
                )
            }
        }
    }

    fun dismissDetectedLanguageBanner() {
        dismissedDetectedWord = _currentWord.value
        _smartMlState.value = _smartMlState.value.copy(showDetectedBanner = false)
    }

    fun clearSuggestedDictPrompt() {
        _smartMlState.value = _smartMlState.value.copy(
            suggestedDictIndex = null,
            suggestedDictName = null
        )
    }

    fun toggleQuickTranslationExpanded(): Boolean {
        val nextExpanded = !repository.isQuickTranslationExpanded()
        repository.setQuickTranslationExpanded(nextExpanded)
        _smartMlState.value = _smartMlState.value.copy(isQuickTranslationExpanded = nextExpanded)
        return nextExpanded
    }

    private fun resolveEffectiveTargetLanguage(sourceLang: String): String {
        val preferredTarget = repository.getTranslationTargetLanguage()
        return if (preferredTarget.equals(sourceLang, ignoreCase = true)) {
            if (sourceLang.equals(AppPreferences.ML_LANG_DE, ignoreCase = true)) {
                AppPreferences.ML_LANG_AR
            } else {
                AppPreferences.ML_LANG_DE
            }
        } else {
            preferredTarget
        }
    }

    // =========================================================================
    // Camera / Image OCR Recognition (Phase 9)
    // =========================================================================

    fun recognizeOcrImage(imageUri: Uri) {
        selectedOcrWordsSet.clear()
        _ocrState.value = OcrUiState(isLoading = true)
        viewModelScope.launch {
            val result = repository.recognizeTextFromUri(imageUri)
            if (result == null) {
                _ocrState.value = OcrUiState(isLoading = false, hasError = true)
            } else {
                _ocrState.value = OcrUiState(
                    isLoading = false,
                    scanResult = result,
                    hasError = false,
                    isPhraseMode = false,
                    selectedPhraseWords = emptySet()
                )
            }
        }
    }

    fun setOcrPhraseMode(enabled: Boolean) {
        if (!enabled) {
            selectedOcrWordsSet.clear()
        }
        _ocrState.value = _ocrState.value.copy(
            isPhraseMode = enabled,
            selectedPhraseWords = selectedOcrWordsSet.toSet()
        )
    }

    fun toggleOcrPhraseWord(word: String) {
        if (selectedOcrWordsSet.contains(word)) {
            selectedOcrWordsSet.remove(word)
        } else {
            selectedOcrWordsSet.add(word)
        }
        _ocrState.value = _ocrState.value.copy(
            selectedPhraseWords = selectedOcrWordsSet.toSet()
        )
    }
}
