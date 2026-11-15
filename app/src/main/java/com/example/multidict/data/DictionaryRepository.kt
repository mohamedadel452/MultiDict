package com.example.multidict.data

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Central list of dictionaries and their "Ink & Ivory" desaturated pastel palettes.
 */
object DictionaryRepository {

    val dictionaries: List<Dictionary> = listOf(
        Dictionary("Duden", "DU", "https://www.duden.de/suchen/dudenonline/{q}", "https://www.duden.de/woerterbuch"),
        Dictionary("Synonyme", "SY", "https://www.duden.de/suchen/synonyme/{q}", "https://www.duden.de/synonyme"),
        Dictionary("Stilwörterbuch", "ST", "https://www.dwds.de/wp/{q}", "https://www.dwds.de/wp"),
        Dictionary("Sprachwissen", "SP", "https://www.duden.de/suchen/sprachwissen/{q}", "https://www.duden.de/sprachwissen"),
        Dictionary("DWDS", "DW", "https://www.dwds.de/wb/{q}", "https://www.dwds.de/"),
        Dictionary("FreeDic", "FD", "https://de.thefreedictionary.com/{q}", "https://de.thefreedictionary.com/"),
        Dictionary("ArabDict", "AD", "https://www.arabdict.com/de/deutsch-arabisch/{q}", "https://www.arabdict.com/"),
        Dictionary("PONS", "PO", "https://en.pons.com/translate/german-arabic/{q}", "https://en.pons.com/translate"),
        Dictionary("Langenscheidt", "LA", "https://de.langenscheidt.com/deutsch-arabisch/{q}", "https://de.langenscheidt.com/deutsch-arabisch/"),
        Dictionary("Verben.de", "VE", "https://www.verben.de/?w={q}", "https://www.verben.de/"),
        Dictionary("Verbformen", "VF", "https://www.verbformen.de/?w={q}", "https://www.verbformen.de/"),
        Dictionary("Wortschatz", "WS", "https://corpora.uni-leipzig.de/de/res?corpusId=deu_news_2018&word={q}", "https://wortschatz.uni-leipzig.de/"),
        Dictionary("Wiktionary", "WK", "https://de.wiktionary.org/wiki/{q}", "https://de.wiktionary.org/"),
        Dictionary("Bilder", "BI", "https://www.google.com/search?tbm=isch&q={q}", "https://www.google.com/imghp"),
        Dictionary("Wikipedia", "WP", "https://de.wikipedia.org/wiki/{q}", "https://de.wikipedia.org/"),
        Dictionary("openthesaurus", "OT", "https://www.openthesaurus.de/synonyme/{q}", "https://www.openthesaurus.de/"),
        Dictionary("Forvo", "FV", "https://de.forvo.com/word/{q}/#de", "https://de.forvo.com/")
    )

    private val fallbackPalettes = listOf(
        DictionaryPalette(R.color.dict_duden_start, R.color.dict_duden_end, R.color.dict_duden_fg),
        DictionaryPalette(R.color.dict_synonyme_start, R.color.dict_synonyme_end, R.color.dict_synonyme_fg),
        DictionaryPalette(R.color.dict_dwds_start, R.color.dict_dwds_end, R.color.dict_dwds_fg),
        DictionaryPalette(R.color.dict_freedic_start, R.color.dict_freedic_end, R.color.dict_freedic_fg),
        DictionaryPalette(R.color.dict_arabdict_start, R.color.dict_arabdict_end, R.color.dict_arabdict_fg),
        DictionaryPalette(R.color.dict_pons_start, R.color.dict_pons_end, R.color.dict_pons_fg),
        DictionaryPalette(R.color.dict_langenscheidt_start, R.color.dict_langenscheidt_end, R.color.dict_langenscheidt_fg),
        DictionaryPalette(R.color.dict_verben_start, R.color.dict_verben_end, R.color.dict_verben_fg),
        DictionaryPalette(R.color.dict_verbformen_start, R.color.dict_verbformen_end, R.color.dict_verbformen_fg),
        DictionaryPalette(R.color.dict_wortschatz_start, R.color.dict_wortschatz_end, R.color.dict_wortschatz_fg),
        DictionaryPalette(R.color.dict_wiktionary_start, R.color.dict_wiktionary_end, R.color.dict_wiktionary_fg),
        DictionaryPalette(R.color.dict_bilder_start, R.color.dict_bilder_end, R.color.dict_bilder_fg)
    )

    fun getPalette(dictionary: Dictionary): DictionaryPalette {
        return when (dictionary.name.lowercase()) {
            "duden" -> DictionaryPalette(R.color.dict_duden_start, R.color.dict_duden_end, R.color.dict_duden_fg)
            "synonyme", "openthesaurus" -> DictionaryPalette(R.color.dict_synonyme_start, R.color.dict_synonyme_end, R.color.dict_synonyme_fg)
            "dwds", "stilwörterbuch" -> DictionaryPalette(R.color.dict_dwds_start, R.color.dict_dwds_end, R.color.dict_dwds_fg)
            "freedic", "forvo" -> DictionaryPalette(R.color.dict_freedic_start, R.color.dict_freedic_end, R.color.dict_freedic_fg)
            "arabdict", "sprachwissen" -> DictionaryPalette(R.color.dict_arabdict_start, R.color.dict_arabdict_end, R.color.dict_arabdict_fg)
            "pons" -> DictionaryPalette(R.color.dict_pons_start, R.color.dict_pons_end, R.color.dict_pons_fg)
            "langenscheidt" -> DictionaryPalette(R.color.dict_langenscheidt_start, R.color.dict_langenscheidt_end, R.color.dict_langenscheidt_fg)
            "verben.de", "verben" -> DictionaryPalette(R.color.dict_verben_start, R.color.dict_verben_end, R.color.dict_verben_fg)
            "verbformen" -> DictionaryPalette(R.color.dict_verbformen_start, R.color.dict_verbformen_end, R.color.dict_verbformen_fg)
            "wortschatz" -> DictionaryPalette(R.color.dict_wortschatz_start, R.color.dict_wortschatz_end, R.color.dict_wortschatz_fg)
            "wiktionary", "wikipedia" -> DictionaryPalette(R.color.dict_wiktionary_start, R.color.dict_wiktionary_end, R.color.dict_wiktionary_fg)
            "bilder" -> DictionaryPalette(R.color.dict_bilder_start, R.color.dict_bilder_end, R.color.dict_bilder_fg)
            else -> fallbackPalettes[abs(dictionary.name.hashCode()) % fallbackPalettes.size]
        }
    }

    /**
     * Creates a circular pastel gradient badge drawable for [dictionary], with an optional thin primary ring when selected.
     */
    fun createBadgeDrawable(context: Context, dictionary: Dictionary, isSelected: Boolean = false): GradientDrawable {
        val palette = getPalette(dictionary)
        val startColor = ContextCompat.getColor(context, palette.startColorRes)
        val endColor = ContextCompat.getColor(context, palette.endColorRes)
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(startColor, endColor)
        ).apply {
            shape = GradientDrawable.OVAL
            if (isSelected) {
                val ringWidth = (1.5f * context.resources.displayMetrics.density).toInt().coerceAtLeast(2)
                setStroke(ringWidth, ContextCompat.getColor(context, R.color.primary))
            }
        }
    }

    fun getBadgeTextColor(context: Context, dictionary: Dictionary): Int {
        val palette = getPalette(dictionary)
        return ContextCompat.getColor(context, palette.fgColorRes)
    }

    fun findByName(context: Context, name: String): Dictionary {
        val builtIn = dictionaries.firstOrNull { it.name == name }
        if (builtIn != null) return builtIn
        val custom = AppPreferences.getCustomDictionaries(context).firstOrNull { it.name == name }
        return custom ?: dictionaries.first()
    }

    /**
     * Builds the target URL for [dictionary] given [rawWord].
     * Returns [Dictionary.homeUrl] if the trimmed word is blank; otherwise replaces "{q}"
     * with the UTF-8 URL-encoded query (supporting German umlauts ä, ö, ü, ß).
     */
    fun buildUrl(dictionary: Dictionary, rawWord: String): String {
        val trimmed = rawWord.trim()
        if (trimmed.isEmpty()) {
            return dictionary.homeUrl
        }
        val encoded = URLEncoder.encode(trimmed, Charsets.UTF_8.name()).replace("+", "%20")
        return dictionary.searchUrl.replace("{q}", encoded)
    }
}
