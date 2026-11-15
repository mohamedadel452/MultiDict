package com.example.multidict.ui

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.FragmentHistoryBinding
import com.example.multidict.databinding.ItemWordRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * MVVM View for Search History:
 * Observes [MainViewModel.historyList] and delegates word selection and history clearing to [MainViewModel].
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }
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

        viewModel.refreshHistory()

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.historyList.collect { updatedHistory ->
                    historyItems.clear()
                    historyItems.addAll(updatedHistory)
                    historyAdapter.notifyDataSetChanged()
                    updateEmptyState()
                }
            }
        }
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_history_confirm_title)
            .setMessage(R.string.clear_history_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                viewModel.clearHistory()
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
