package com.example.multidict

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.multidict.databinding.DialogAddDictionaryBinding
import com.example.multidict.databinding.FragmentDictionariesOrderBinding
import com.example.multidict.databinding.ItemDictionaryOrderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Collections

/**
 * Dictionary Manager ("Dictionaries / Wörterbücher"):
 * - Show/hide checkboxes for each dictionary
 * - Drag handles (ItemTouchHelper) and tonal up-arrow button to reorder
 * - "+ Add custom dictionary" button with Name + URL (must contain {q}) validation
 */
class DictionariesOrderFragment : Fragment() {

    private var _binding: FragmentDictionariesOrderBinding? = null
    private val binding get() = _binding!!

    private val items = mutableListOf<Dictionary>()
    private lateinit var orderAdapter: OrderAdapter
    private lateinit var touchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictionariesOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        items.clear()
        items.addAll(AppPreferences.getAllOrderedDictionaries(requireContext()))

        binding.btnBackDictionaries.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            parentFragmentManager.popBackStack()
        }

        binding.btnAddCustomDictionary.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            showAddCustomDictionaryDialog()
        }

        orderAdapter = OrderAdapter()
        binding.rvOrderDictionaries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrderDictionaries.adapter = orderAdapter

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                if (fromPos !in items.indices || toPos !in items.indices) return false
                Collections.swap(items, fromPos, toPos)
                orderAdapter.notifyItemMoved(fromPos, toPos)
                persistChanges()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.rvOrderDictionaries)
    }

    private fun showAddCustomDictionaryDialog() {
        val dialogBinding = DialogAddDictionaryBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_add_dict_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.add, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.etCustomDictName.text.toString().trim()
                val url = dialogBinding.etCustomDictUrl.text.toString().trim()

                if (name.isEmpty()) {
                    dialogBinding.etCustomDictName.error = getString(R.string.error_invalid_dict_name)
                    return@setOnClickListener
                }
                val isValidScheme = url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true)
                if (!isValidScheme || !url.contains("{q}")) {
                    dialogBinding.etCustomDictUrl.error = getString(R.string.error_invalid_dict_url)
                    return@setOnClickListener
                }

                val ctx = requireContext()
                val newDict = AppPreferences.addCustomDictionary(ctx, name, url)
                AppPreferences.setDictionaryEnabled(ctx, newDict.name, true)

                items.removeAll { d -> d.name.equals(newDict.name, ignoreCase = true) }
                items.add(newDict)
                orderAdapter.notifyDataSetChanged()
                persistChanges()
                binding.rvOrderDictionaries.scrollToPosition(items.lastIndex)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun moveItemUp(position: Int) {
        if (position <= 0 || position >= items.size) return
        Collections.swap(items, position, position - 1)
        orderAdapter.notifyItemMoved(position, position - 1)
        orderAdapter.notifyItemRangeChanged(position - 1, 2)
        persistChanges()
    }

    private fun persistChanges() {
        val ctx = context ?: return
        AppPreferences.saveDictionaryOrder(ctx, items)
        (activity as? MainActivity)?.onDictionaryOrderChanged()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private inner class OrderAdapter : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
            val itemBinding = ItemDictionaryOrderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return OrderViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size

        inner class OrderViewHolder(
            private val itemBinding: ItemDictionaryOrderBinding
        ) : RecyclerView.ViewHolder(itemBinding.root) {

            @SuppressLint("ClickableViewAccessibility")
            fun bind(dictionary: Dictionary, position: Int) {
                val ctx = itemBinding.root.context
                itemBinding.tvOrderBadge.text = dictionary.shortName
                itemBinding.tvOrderName.text = dictionary.name

                itemBinding.tvOrderBadge.background = DictionaryRepository.createBadgeDrawable(
                    context = ctx,
                    dictionary = dictionary,
                    isSelected = false
                )
                itemBinding.tvOrderBadge.setTextColor(
                    DictionaryRepository.getBadgeTextColor(ctx, dictionary)
                )

                val isEnabled = AppPreferences.isDictionaryEnabled(ctx, dictionary.name)
                itemBinding.cbEnabled.setOnCheckedChangeListener(null)
                itemBinding.cbEnabled.isChecked = isEnabled
                itemBinding.tvOrderName.alpha = if (isEnabled) 1.0f else 0.5f
                itemBinding.tvOrderBadge.alpha = if (isEnabled) 1.0f else 0.5f

                AppPreferences.animateListItem(itemBinding.root, position)

                itemBinding.cbEnabled.setOnCheckedChangeListener { buttonView, isChecked ->
                    if (!isChecked) {
                        val enabledCount = items.count { AppPreferences.isDictionaryEnabled(ctx, it.name) }
                        if (enabledCount <= 1) {
                            buttonView.isChecked = true
                            Toast.makeText(ctx, R.string.at_least_one_dictionary, Toast.LENGTH_SHORT).show()
                            return@setOnCheckedChangeListener
                        }
                    }
                    AppPreferences.vibrateIfEnabled(buttonView)
                    AppPreferences.setDictionaryEnabled(ctx, dictionary.name, isChecked)
                    itemBinding.tvOrderName.alpha = if (isChecked) 1.0f else 0.5f
                    itemBinding.tvOrderBadge.alpha = if (isChecked) 1.0f else 0.5f
                    (activity as? MainActivity)?.onDictionaryOrderChanged()
                }

                itemBinding.ivDragHandle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        touchHelper.startDrag(this)
                    }
                    false
                }

                itemBinding.btnMoveUp.alpha = if (position == 0) 0.4f else 1.0f
                itemBinding.btnMoveUp.setOnClickListener {
                    val currentPos = bindingAdapterPosition
                    if (currentPos > 0) {
                        AppPreferences.vibrateIfEnabled(it)
                        moveItemUp(currentPos)
                    }
                }
            }
        }
    }
}
