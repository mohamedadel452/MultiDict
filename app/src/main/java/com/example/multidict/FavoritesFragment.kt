package com.example.multidict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.FragmentFavoritesBinding
import com.example.multidict.databinding.ItemFavoriteRowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Displays the user's saved favorite words along with their offline personal notes/meanings.
 * - Tap a word to search it immediately
 * - Tap the edit icon (✎) to attach/edit an offline personal note
 * - Swipe left or right to delete from favorites
 */
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val favoriteItems = mutableListOf<String>()
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

        favoriteItems.clear()
        favoriteItems.addAll(AppPreferences.getFavorites(requireContext()))

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
                    val removedWord = favoriteItems.removeAt(pos)
                    AppPreferences.removeFavorite(requireContext(), removedWord)
                    favoritesAdapter.notifyItemRemoved(pos)
                    updateEmptyState()
                    (activity as? MainActivity)?.updateFavoriteIcon()
                }
            }
        })
        swipeHelper.attachToRecyclerView(binding.rvFavorites)

        updateEmptyState()
    }

    private fun showEditNoteDialog(word: String, position: Int) {
        val ctx = requireContext()
        val currentNote = AppPreferences.getWordNote(ctx, word)

        val container = FrameLayout(ctx).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val input = EditText(ctx).apply {
            hint = getString(R.string.edit_note_hint)
            setText(currentNote)
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
            .setTitle(getString(R.string.edit_note_title, word))
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                AppPreferences.setWordNote(ctx, word, input.text.toString())
                if (position in favoriteItems.indices) {
                    favoritesAdapter.notifyItemChanged(position)
                }
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

            fun bind(word: String, position: Int) {
                val ctx = itemBinding.root.context
                itemBinding.tvFavoriteWord.text = word

                val note = AppPreferences.getWordNote(ctx, word)
                if (note.isNotEmpty()) {
                    itemBinding.tvFavoriteNote.text = note
                    itemBinding.tvFavoriteNote.alpha = 1.0f
                } else {
                    itemBinding.tvFavoriteNote.text = getString(R.string.favorite_note_placeholder)
                    itemBinding.tvFavoriteNote.alpha = 0.65f
                }

                AppPreferences.animateListItem(itemBinding.root, position)

                itemBinding.rowFavoriteRoot.setOnClickListener {
                    AppPreferences.vibrateIfEnabled(it)
                    (activity as? MainActivity)?.searchWordFromList(word)
                    parentFragmentManager.popBackStack()
                }

                itemBinding.btnEditNote.setOnClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        AppPreferences.vibrateIfEnabled(it)
                        showEditNoteDialog(word, pos)
                    }
                }
            }
        }
    }
}
