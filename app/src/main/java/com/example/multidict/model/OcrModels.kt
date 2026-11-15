package com.example.multidict.model

import android.graphics.Rect

/**
 * Represents a single recognized word from ML Kit OCR along with its bounding box in image coordinates.
 */
data class OcrWordItem(
    val cleanedWord: String,
    val rawText: String,
    val boundingBox: Rect?
)

/**
 * Complete result of running on-device ML Kit OCR on an image.
 */
data class OcrScanResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val words: List<OcrWordItem>,
    val uniqueWords: List<String>
)
