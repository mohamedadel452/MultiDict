package com.example.multidict.ui

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.multidict.databinding.DialogOcrResultsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * MVVM View for Camera / Image OCR Scan:
 * Observes [MainViewModel.ocrState] and delegates ML Kit text recognition & phrase selection to [MainViewModel].
 */
class OcrScanDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogOcrResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }
    private var imageUri: Uri? = null
    private val chipMap = mutableMapOf<String, Chip>()
    private var populatedWordsList: List<String> = emptyList()

    companion object {
        private const val ARG_IMAGE_URI = "arg_image_uri"

        fun newInstance(uri: Uri): OcrScanDialogFragment {
            return OcrScanDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_URI, uri.toString())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uriStr = arguments?.getString(ARG_IMAGE_URI)
        if (!uriStr.isNullOrBlank()) {
            imageUri = Uri.parse(uriStr)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogOcrResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseOcr.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            dismissAllowingStateLoss()
        }

        binding.rowPhraseMode.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val newState = !binding.swPhraseMode.isChecked
            binding.swPhraseMode.isChecked = newState
            viewModel.setOcrPhraseMode(newState)
        }

        binding.btnSearchPhrase.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val phrase = viewModel.ocrState.value.selectedPhraseWords.joinToString(" ").trim()
            if (phrase.isNotEmpty()) {
                (activity as? MainActivity)?.onSmartInputReceived(phrase)
                dismissAllowingStateLoss()
            }
        }

        binding.ocrHighlightOverlay.onWordBoxTapped = { tappedWord ->
            handleWordTap(tappedWord)
        }

        val targetUri = imageUri
        if (targetUri == null) {
            showErrorState()
            return
        }

        binding.ivOcrPreview.setImageURI(targetUri)
        viewModel.recognizeOcrImage(targetUri)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ocrState.collect { state ->
                    renderOcrState(state)
                }
            }
        }
    }

    private fun renderOcrState(state: OcrUiState) {
        val b = _binding ?: return
        b.layoutOcrLoading.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        if (state.isLoading) return

        if (state.hasError || state.scanResult == null) {
            showErrorState()
            return
        }

        val result = state.scanResult
        if (result.uniqueWords.isEmpty()) {
            b.tvOcrEmpty.setText(R.string.ocr_empty_result)
            b.tvOcrEmpty.visibility = View.VISIBLE
            b.scrollOcrChips.visibility = View.GONE
            return
        }

        b.tvOcrEmpty.visibility = View.GONE
        b.scrollOcrChips.visibility = View.VISIBLE

        if (populatedWordsList != result.uniqueWords) {
            populatedWordsList = result.uniqueWords
            b.ocrHighlightOverlay.setOcrData(result.imageWidth, result.imageHeight, result.words)
            populateWordChips(result.uniqueWords)
        }

        syncSelectionUi(state.selectedPhraseWords)
    }

    private fun populateWordChips(words: List<String>) {
        val ctx = context ?: return
        val b = _binding ?: return
        b.chipGroupOcrWords.removeAllViews()
        chipMap.clear()

        for (word in words) {
            val chip = Chip(ctx).apply {
                text = word
                isCheckable = true
                isClickable = true
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.surface_container)
                )
                chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.outline)
                )
                chipStrokeWidth = resources.getDimension(R.dimen.hairline)
                setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
                setOnClickListener {
                    AppPreferences.vibrateIfEnabled(this)
                    handleWordTap(word)
                }
            }
            chipMap[word] = chip
            b.chipGroupOcrWords.addView(chip)
        }
    }

    private fun handleWordTap(word: String) {
        val b = _binding ?: return
        if (!b.swPhraseMode.isChecked) {
            (activity as? MainActivity)?.onSmartInputReceived(word)
            dismissAllowingStateLoss()
        } else {
            viewModel.toggleOcrPhraseWord(word)
        }
    }

    private fun syncSelectionUi(selectedWords: Set<String>) {
        val ctx = context ?: return
        val b = _binding ?: return
        b.ocrHighlightOverlay.setSelectedWords(selectedWords)

        val selectedBg = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_container))
        val normalBg = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.surface_container))
        val selectedStroke = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary))
        val normalStroke = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.outline))

        for ((word, chip) in chipMap) {
            val isSel = selectedWords.contains(word)
            chip.isChecked = isSel
            chip.chipBackgroundColor = if (isSel) selectedBg else normalBg
            chip.chipStrokeColor = if (isSel) selectedStroke else normalStroke
        }

        if (selectedWords.isNotEmpty()) {
            val phrase = selectedWords.joinToString(" ")
            b.btnSearchPhrase.text = getString(R.string.ocr_search_phrase_btn, phrase)
            b.btnSearchPhrase.visibility = View.VISIBLE
        } else {
            b.btnSearchPhrase.visibility = View.GONE
        }
    }

    private fun showErrorState() {
        val b = _binding ?: return
        b.layoutOcrLoading.visibility = View.GONE
        b.tvOcrEmpty.setText(R.string.ocr_error_result)
        b.tvOcrEmpty.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
