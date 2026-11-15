package com.example.multidict.data

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine-based helper for Phase 9 On-Device Machine Learning:
 * 1. ML Kit Language Identification (DE / EN / AR)
 * 2. ML Kit On-Device Translation + Model Manager (Download / Check / Delete)
 * 3. ML Kit Text Recognition (Latin script OCR with hyphenation cleanup)
 */
object SmartMlHelper {

    private val arabicScriptRegex = Regex("[\\u0600-\\u06FF]")
    private val germanCharRegex = Regex("[äöüßÄÖÜ]")
    private val trimPunctuationRegex = Regex("^[.,;:!?\"'()\\[\\]{}«»„“”‚‘‹›\\-–—_0-9]+|[.,;:!?\"'()\\[\\]{}«»„“”‚‘‹›\\-–—_0-9]+$")

    /**
     * Suspends until a Google Play Services [Task] completes without blocking the main thread.
     */
    suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exception ->
            if (cont.isActive) cont.resumeWithException(exception)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }

    /**
     * Identifies whether [text] is German ("de"), English ("en"), or Arabic ("ar").
     * Combines ML Kit Language Identification with script heuristics for very short single words.
     */
    suspend fun identifyLanguage(text: String): String? = withContext(Dispatchers.Default) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext null

        // Fast & certain script check for Arabic characters
        if (arabicScriptRegex.containsMatchIn(trimmed)) {
            return@withContext AppPreferences.ML_LANG_AR
        }
        // Fast check for German umlauts / Eszett
        if (germanCharRegex.containsMatchIn(trimmed)) {
            return@withContext AppPreferences.ML_LANG_DE
        }

        val client = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.34f)
                .build()
        )
        try {
            val langCode = client.identifyLanguage(trimmed).awaitResult()
            when {
                langCode.startsWith("de", ignoreCase = true) -> AppPreferences.ML_LANG_DE
                langCode.startsWith("en", ignoreCase = true) -> AppPreferences.ML_LANG_EN
                langCode.startsWith("ar", ignoreCase = true) -> AppPreferences.ML_LANG_AR
                else -> {
                    // For short capitalized nouns typical in German lookup, default to German if ML Kit is undecided
                    if (langCode == "und" && trimmed.first().isUpperCase() && trimmed.all { it.isLetter() }) {
                        AppPreferences.ML_LANG_DE
                    } else {
                        null
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
    }

    // --- On-Device Translation & Model Management ---

    fun toTranslateLanguage(code: String): String {
        return when (code.lowercase()) {
            AppPreferences.ML_LANG_AR -> TranslateLanguage.ARABIC
            AppPreferences.ML_LANG_EN -> TranslateLanguage.ENGLISH
            else -> TranslateLanguage.GERMAN
        }
    }

    suspend fun isModelDownloaded(langCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val model = TranslateRemoteModel.Builder(toTranslateLanguage(langCode)).build()
            RemoteModelManager.getInstance().isModelDownloaded(model).awaitResult()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun arePairModelsDownloaded(sourceLang: String, targetLang: String): Boolean {
        return isModelDownloaded(sourceLang) && isModelDownloaded(targetLang)
    }

    suspend fun downloadLanguageModel(langCode: String, preferWifi: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            val mlLang = toTranslateLanguage(langCode)
            // Creating a translator with English or German as companion ensures downloadModelIfNeeded fetches mlLang
            val companion = if (mlLang == TranslateLanguage.GERMAN) {
                TranslateLanguage.ENGLISH
            } else {
                TranslateLanguage.GERMAN
            }
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(mlLang)
                .setTargetLanguage(companion)
                .build()
            val translator = Translation.getClient(options)
            try {
                val conditionsBuilder = DownloadConditions.Builder()
                if (preferWifi) {
                    conditionsBuilder.requireWifi()
                }
                translator.downloadModelIfNeeded(conditionsBuilder.build()).awaitResult()
                true
            } catch (_: Exception) {
                false
            } finally {
                try {
                    translator.close()
                } catch (_: Exception) {
                }
            }
        }

    suspend fun downloadPairModels(
        sourceLang: String,
        targetLang: String,
        preferWifi: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(toTranslateLanguage(sourceLang))
            .setTargetLanguage(toTranslateLanguage(targetLang))
            .build()
        val translator = Translation.getClient(options)
        try {
            val conditionsBuilder = DownloadConditions.Builder()
            if (preferWifi) {
                conditionsBuilder.requireWifi()
            }
            translator.downloadModelIfNeeded(conditionsBuilder.build()).awaitResult()
            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                translator.close()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun deleteLanguageModel(langCode: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val model = TranslateRemoteModel.Builder(toTranslateLanguage(langCode)).build()
            RemoteModelManager.getInstance().deleteDownloadedModel(model).awaitResult()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Translates [text] from [sourceLang] to [targetLang] strictly on-device (assuming models are downloaded).
     */
    suspend fun translateOnDevice(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? = withContext(Dispatchers.Default) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || sourceLang.equals(targetLang, ignoreCase = true)) {
            return@withContext null
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(toTranslateLanguage(sourceLang))
            .setTargetLanguage(toTranslateLanguage(targetLang))
            .build()
        val translator = Translation.getClient(options)
        try {
            val result = translator.translate(trimmed).awaitResult().trim()
            result.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            try {
                translator.close()
            } catch (_: Exception) {
            }
        }
    }

    // --- On-Device OCR (Latin Script Text Recognition) ---

    /**
     * Runs bundled ML Kit Latin Text Recognition on [imageUri], joining hyphenated line-breaks
     * and cleaning punctuation around recognized words.
     */
    suspend fun recognizeTextFromUri(context: Context, imageUri: Uri): OcrScanResult? =
        withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                val visionText = recognizer.process(inputImage).awaitResult()

                val wordItems = mutableListOf<OcrWordItem>()
                val uniqueOrdered = LinkedHashSet<String>()

                for (block in visionText.textBlocks) {
                    val lines = block.lines
                    var skipFirstElementOfNextLine = false

                    for (lineIdx in lines.indices) {
                        val line = lines[lineIdx]
                        val elements = line.elements
                        if (elements.isEmpty()) continue

                        for (elemIdx in elements.indices) {
                            if (elemIdx == 0 && skipFirstElementOfNextLine) {
                                skipFirstElementOfNextLine = false
                                continue
                            }

                            val element = elements[elemIdx]
                            var raw = element.text.trim()
                            var box = element.boundingBox

                            // Check if this is the last word on the line and ends with a hyphen ("Wörter-")
                            val isLastInLine = elemIdx == elements.lastIndex
                            if (isLastInLine &&
                                (raw.endsWith("-") || raw.endsWith("‐") || raw.endsWith("­")) &&
                                lineIdx + 1 < lines.size
                            ) {
                                val nextLineElements = lines[lineIdx + 1].elements
                                if (nextLineElements.isNotEmpty()) {
                                    val nextElem = nextLineElements.first()
                                    val prefix = raw.dropLast(1)
                                    val suffix = nextElem.text.trim()
                                    if (prefix.any { it.isLetter() } && suffix.any { it.isLetter() }) {
                                        raw = prefix + suffix
                                        if (box != null && nextElem.boundingBox != null) {
                                            box = Rect(box).apply { union(nextElem.boundingBox!!) }
                                        }
                                        skipFirstElementOfNextLine = true
                                    }
                                }
                            }

                            val cleaned = cleanOcrWord(raw)
                            if (cleaned.length >= 2 && cleaned.any { it.isLetter() }) {
                                wordItems.add(
                                    OcrWordItem(
                                        cleanedWord = cleaned,
                                        rawText = raw,
                                        boundingBox = box
                                    )
                                )
                                uniqueOrdered.add(cleaned)
                            }
                        }
                    }
                }

                OcrScanResult(
                    imageWidth = inputImage.width.coerceAtLeast(1),
                    imageHeight = inputImage.height.coerceAtLeast(1),
                    words = wordItems,
                    uniqueWords = uniqueOrdered.toList()
                )
            } catch (_: Exception) {
                null
            } finally {
                try {
                    recognizer.close()
                } catch (_: Exception) {
                }
            }
        }

    fun cleanOcrWord(raw: String): String {
        return raw.trim()
            .replace(trimPunctuationRegex, "")
            .trim()
    }
}
