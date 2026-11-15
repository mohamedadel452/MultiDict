package com.example.multidict.ui.adapter

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.ItemSuggestionBinding

/**
 * RecyclerView adapter displaying autocomplete suggestions below the search pill.
 * Highlights the matched substring of [currentQuery] in bold primary color.
 */
class SuggestionAdapter(
    private val onSuggestionClicked: (String) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    private val items = mutableListOf<String>()
    private var currentQuery: String = ""

    fun submitList(newItems: List<String>, query: String = "") {
        currentQuery = query.trim()
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val binding = ItemSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SuggestionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class SuggestionViewHolder(
        private val binding: ItemSuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(suggestion: String, position: Int) {
            val ctx = binding.root.context
            val primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            val rippleTint = ContextCompat.getColor(ctx, R.color.primary_container)

            // Apply primaryContainer ripple to row
            binding.rowSuggestion.background = RippleDrawable(
                ColorStateList.valueOf(rippleTint),
                null,
                null
            )

            // Highlight matched query substring in bold primary color
            if (currentQuery.isNotEmpty()) {
                val matchIndex = suggestion.indexOf(currentQuery, ignoreCase = true)
                if (matchIndex >= 0) {
                    val end = (matchIndex + currentQuery.length).coerceAtMost(suggestion.length)
                    val ssb = SpannableStringBuilder(suggestion)
                    ssb.setSpan(
                        ForegroundColorSpan(primaryColor),
                        matchIndex,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    ssb.setSpan(
                        StyleSpan(Typeface.BOLD),
                        matchIndex,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    binding.tvSuggestion.text = ssb
                } else {
                    binding.tvSuggestion.text = suggestion
                }
            } else {
                binding.tvSuggestion.text = suggestion
            }

            AppPreferences.animateListItem(binding.root, position)

            binding.rowSuggestion.setOnClickListener {
                AppPreferences.vibrateIfEnabled(it)
                onSuggestionClicked(suggestion)
            }
        }
    }
}
