package com.example.multidict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.FragmentHistoryBinding
import com.example.multidict.databinding.ItemWordRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Displays the user's searched words (newest first, max 200).
 * Tapping a word searches it immediately; "Clear history" shows a Material 3 confirmation dialog.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyItems = mutableListOf<String>()
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        historyItems.clear()
        historyItems.addAll(AppPreferences.getHistory(requireContext()))

        binding.btnBackHistory.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            parentFragmentManager.popBackStack()
        }

        binding.btnClearHistory.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            if (historyItems.isNotEmpty()) {
                showClearHistoryDialog()
            }
        }

        historyAdapter = HistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        updateEmptyState()
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_history_confirm_title)
            .setMessage(R.string.clear_history_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                AppPreferences.clearHistory(requireContext())
                historyItems.clear()
                historyAdapter.notifyDataSetChanged()
                updateEmptyState()
            }
            .show()
    }

    private fun updateEmptyState() {
        val isEmpty = historyItems.isEmpty()
        binding.tvEmptyHistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.btnClearHistory.visibility = if (isEmpty) View.INVISIBLE else View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.WordViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
            val itemBinding = ItemWordRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return WordViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
            holder.bind(historyItems[position], position)
        }

        override fun getItemCount(): Int = historyItems.size

        inner class WordViewHolder(
            private val itemBinding: ItemWordRowBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(word: String, position: Int) {
                itemBinding.tvWord.text = word
                AppPreferences.animateListItem(itemBinding.root, position)
                itemBinding.tvWord.setOnClickListener {
                    AppPreferences.vibrateIfEnabled(it)
                    (activity as? MainActivity)?.searchWordFromList(word)
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }
}
