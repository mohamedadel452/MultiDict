package com.example.multidict.model

/**
 * Domain model representing a saved favorite word and its optional personal study note.
 */
data class FavoriteWordItem(
    val word: String,
    val note: String
)
