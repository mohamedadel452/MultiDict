package com.example.multidict.model

import androidx.annotation.ColorRes

/**
 * Represents a single online dictionary source.
 * [searchUrl] must contain "{q}" where the URL-encoded query word will be inserted.
 */
data class Dictionary(
    val name: String,
    val shortName: String,
    val searchUrl: String,
    val homeUrl: String
) {
    val stableId: Long get() = name.hashCode().toLong()
}

/**
 * Pastel gradient color pair + darker hue foreground tone for a dictionary badge.
 */
data class DictionaryPalette(
    @ColorRes val startColorRes: Int,
    @ColorRes val endColorRes: Int,
    @ColorRes val fgColorRes: Int
)
