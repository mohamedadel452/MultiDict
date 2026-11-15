package com.example.multidict

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2 adapter that creates a [DictionaryFragment] for each dictionary in [dictionaries].
 * Uses stable item IDs so reordering dictionaries in "Wörterbücher" preserves loaded WebViews.
 */
class DictionaryPagerAdapter(
    activity: FragmentActivity,
    private val dictionaries: MutableList<Dictionary>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = dictionaries.size

    override fun getItemId(position: Int): Long {
        return dictionaries[position].stableId
    }

    override fun containsItem(itemId: Long): Boolean {
        return dictionaries.any { it.stableId == itemId }
    }

    override fun createFragment(position: Int): Fragment {
        return DictionaryFragment.newInstance(dictionaries[position].name)
    }
}
