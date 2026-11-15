package com.example.multidict

import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.ItemDictionaryTabBinding

/**
 * Horizontal RecyclerView adapter for the floating bottom dictionary bar.
 * Each tab has a 40dp pastel gradient circle with darker hue initials and Label text below.
 * Selected tab receives a surfaceContainerHigh pill that animates in (scale + fade, 200ms),
 * semibold primary label, and a thin primary ring around the circle.
 */
class DictionaryAdapter(
    private val items: MutableList<Dictionary>,
    selectedIndex: Int,
    private val onTabSelected: (Int, Dictionary) -> Unit
) : RecyclerView.Adapter<DictionaryAdapter.TabViewHolder>() {

    var selectedPosition: Int = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        private set

    private var lastAnimatedSelectedPos: Int = selectedPosition

    fun selectPosition(newPosition: Int) {
        if (newPosition !in items.indices || newPosition == selectedPosition) return
        val previous = selectedPosition
        selectedPosition = newPosition
        notifyItemChanged(previous)
        notifyItemChanged(selectedPosition)
    }

    fun updateItems(newItems: List<Dictionary>, newSelectedIndex: Int) {
        items.clear()
        items.addAll(newItems)
        selectedPosition = newSelectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        lastAnimatedSelectedPos = selectedPosition
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemDictionaryTabBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.bind(items[position], position == selectedPosition, position)
    }

    override fun getItemCount(): Int = items.size

    inner class TabViewHolder(
        private val binding: ItemDictionaryTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(dictionary: Dictionary, isSelected: Boolean, position: Int) {
            val context = binding.root.context
            binding.tvBadge.text = dictionary.shortName
            binding.tvName.text = dictionary.name

            // Unique soft pastel gradient circle + darker hue initials + thin primary ring when selected
            binding.tvBadge.background = DictionaryRepository.createBadgeDrawable(
                context = context,
                dictionary = dictionary,
                isSelected = isSelected
            )
            binding.tvBadge.setTextColor(DictionaryRepository.getBadgeTextColor(context, dictionary))

            if (isSelected) {
                binding.tabRoot.setBackgroundResource(R.drawable.bg_tab_selected)
                binding.tvName.setTextColor(ContextCompat.getColor(context, R.color.primary))
                binding.tvName.setTypeface(binding.tvName.typeface, Typeface.BOLD)

                if (lastAnimatedSelectedPos != position && AppPreferences.areAnimationsEnabled(context)) {
                    lastAnimatedSelectedPos = position
                    binding.tabRoot.scaleX = 0.92f
                    binding.tabRoot.scaleY = 0.92f
                    binding.tabRoot.alpha = 0.7f
                    binding.tabRoot.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(200L)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                } else {
                    binding.tabRoot.scaleX = 1f
                    binding.tabRoot.scaleY = 1f
                    binding.tabRoot.alpha = 1f
                }
            } else {
                binding.tabRoot.setBackgroundColor(ContextCompat.getColor(context, R.color.tab_unselected_bg))
                binding.tvName.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.tvName.setTypeface(binding.tvName.typeface, Typeface.NORMAL)
                binding.tabRoot.scaleX = 1f
                binding.tabRoot.scaleY = 1f
                binding.tabRoot.alpha = 1f
            }

            binding.tabRoot.setOnClickListener {
                val adapterPos = bindingAdapterPosition
                if (adapterPos != RecyclerView.NO_POSITION) {
                    if (AppPreferences.isVibrateOnClickEnabled(context)) {
                        it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    selectPosition(adapterPos)
                    onTabSelected(adapterPos, items[adapterPos])
                }
            }
        }
    }
}
