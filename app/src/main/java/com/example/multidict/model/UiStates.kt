package com.example.multidict.model

import com.example.multidict.data.AppPreferences

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
