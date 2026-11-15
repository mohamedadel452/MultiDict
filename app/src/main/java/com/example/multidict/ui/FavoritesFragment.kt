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
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.FragmentFavoritesBinding
import com.example.multidict.databinding.ItemFavoriteRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * MVVM View for Favorites & Offline Personal Notes:
 * Observes [MainViewModel.favoritesList] and delegates note editing and swipe deletion to [MainViewModel].
 */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }
    private val favoriteItems = mutableListOf<FavoriteWordItem>()
    private lateinit var favoritesAdapter: FavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.refreshFavorites()

        binding.btnBackFavorites.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            parentFragmentManager.popBackStack()
        }

        favoritesAdapter = FavoritesAdapter()
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = favoritesAdapter

        val swipeHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos in favoriteItems.indices) {
                    val removedItem = favoriteItems[pos]
                    viewModel.removeFavorite(removedItem.word)
                    (activity as? MainActivity)?.updateFavoriteIcon()
                }
            }
        })
        swipeHelper.attachToRecyclerView(binding.rvFavorites)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.favoritesList.collect { updatedFavorites ->
                    favoriteItems.clear()
                    favoriteItems.addAll(updatedFavorites)
                    favoritesAdapter.notifyDataSetChanged()
                    updateEmptyState()
                }
            }
        }
    }

    private fun showEditNoteDialog(item: FavoriteWordItem) {
        val ctx = requireContext()

        val container = FrameLayout(ctx).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val input = EditText(ctx).apply {
            hint = getString(R.string.edit_note_hint)
            setText(item.note)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(ctx, R.color.text_hint))
            setBackgroundResource(R.drawable.bg_search_input)
            val padH = (16 * resources.displayMetrics.density).toInt()
            val padV = (12 * resources.displayMetrics.density).toInt()
            setPadding(padH, padV, padH, padV)
            setSelection(text.length)
        }
        container.addView(input)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.edit_note_title, item.word))
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.updateWordNote(item.word, input.text.toString())
            }
            .show()
    }

    private fun updateEmptyState() {
        val isEmpty = favoriteItems.isEmpty()
        binding.tvEmptyFavorites.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFavorites.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.tvSwipeHint.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private inner class FavoritesAdapter : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
            val itemBinding = ItemFavoriteRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return FavoriteViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
            holder.bind(favoriteItems[position], position)
        }

        override fun getItemCount(): Int = favoriteItems.size

        inner class FavoriteViewHolder(
            private val itemBinding: ItemFavoriteRowBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(item: FavoriteWordItem, position: Int) {
                itemBinding.tvFavoriteWord.text = item.word

                if (item.note.isNotEmpty()) {
                    itemBinding.tvFavoriteNote.text = item.note
                    itemBinding.tvFavoriteNote.alpha = 1.0f
                } else {
                    itemBinding.tvFavoriteNote.text = getString(R.string.favorite_note_placeholder)
                    itemBinding.tvFavoriteNote.alpha = 0.65f
                }

                AppPreferences.animateListItem(itemBinding.root, position)

                itemBinding.rowFavoriteRoot.setOnClickListener {
                    AppPreferences.vibrateIfEnabled(it)
                    (activity as? MainActivity)?.searchWordFromList(item.word)
                    parentFragmentManager.popBackStack()
                }

                itemBinding.btnEditNote.setOnClickListener {
                    AppPreferences.vibrateIfEnabled(it)
                    showEditNoteDialog(item)
                }
            }
        }
    }
}
