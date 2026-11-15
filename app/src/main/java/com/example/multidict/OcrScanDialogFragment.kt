package com.example.multidict

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.multidict.databinding.DialogOcrResultsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

/**
 * Bottom sheet displaying the captured/picked image with tappable highlighted word bounding boxes
 * and tappable word chips recognized via ML Kit On-Device Text Recognition.
 * Supports single-tap word lookup and multi-word phrase selection.
 */
class OcrScanDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogOcrResultsBinding? = null
    private val binding get() = _binding!!

    private var imageUri: Uri? = null
    private val selectedPhraseWords = LinkedHashSet<String>()
    private val chipMap = mutableMapOf<String, Chip>()

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
            if (!newState) {
                selectedPhraseWords.clear()
                syncSelectionState()
            }
        }

        binding.btnSearchPhrase.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val phrase = selectedPhraseWords.joinToString(" ").trim()
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

        viewLifecycleOwner.lifecycleScope.launch {
            val result = SmartMlHelper.recognizeTextFromUri(requireContext(), targetUri)
            val b = _binding ?: return@launch
            b.layoutOcrLoading.visibility = View.GONE

            if (result == null) {
                showErrorState()
                return@launch
            }

            if (result.uniqueWords.isEmpty()) {
                b.tvOcrEmpty.setText(R.string.ocr_empty_result)
                b.tvOcrEmpty.visibility = View.VISIBLE
                return@launch
            }

            b.ocrHighlightOverlay.setOcrData(result.imageWidth, result.imageHeight, result.words)
            populateWordChips(result.uniqueWords)
            b.scrollOcrChips.visibility = View.VISIBLE
        }
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
            // Immediate single-word search
            (activity as? MainActivity)?.onSmartInputReceived(word)
            dismissAllowingStateLoss()
        } else {
            // Multi-word phrase builder
            if (selectedPhraseWords.contains(word)) {
                selectedPhraseWords.remove(word)
            } else {
                selectedPhraseWords.add(word)
            }
            syncSelectionState()
        }
    }

    private fun syncSelectionState() {
        val ctx = context ?: return
        val b = _binding ?: return
        b.ocrHighlightOverlay.setSelectedWords(selectedPhraseWords)

        val selectedBg = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary_container))
        val normalBg = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.surface_container))
        val selectedStroke = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.primary))
        val normalStroke = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.outline))

        for ((word, chip) in chipMap) {
            val isSel = selectedPhraseWords.contains(word)
            chip.isChecked = isSel
            chip.chipBackgroundColor = if (isSel) selectedBg else normalBg
            chip.chipStrokeColor = if (isSel) selectedStroke else normalStroke
        }

        if (selectedPhraseWords.isNotEmpty()) {
            val phrase = selectedPhraseWords.joinToString(" ")
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
