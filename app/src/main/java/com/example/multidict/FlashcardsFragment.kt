package com.example.multidict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.multidict.databinding.FragmentFlashcardsBinding
import kotlinx.coroutines.launch

/**
 * MVVM View for 3D Flashcards study:
 * Observes [MainViewModel.flashcardState] and delegates deck progression and card flipping to [MainViewModel].
 */
class FlashcardsFragment : Fragment() {

    private var _binding: FragmentFlashcardsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by lazy {
        ViewModelProvider(requireActivity())[MainViewModel::class.java]
    }
    private var isFlipping: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFlashcardsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.initFlashcardDeck()

        binding.btnBackFlashcards.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            parentFragmentManager.popBackStack()
        }

        // Configure 3D camera distance for smooth perspective rotationY
        val scale = resources.displayMetrics.density
        binding.cardFlashcard.cameraDistance = 8000f * scale

        binding.cardFlashcard.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            flipCard3D()
        }

        binding.btnSpeakCard.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val word = viewModel.flashcardState.value.currentWord
            if (word.isNotBlank()) {
                (activity as? MainActivity)?.speakWord(word)
            }
        }

        binding.btnReviewAgain.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            binding.cardFlashcard.rotationY = 0f
            viewModel.markFlashcardReviewAgain()
        }

        binding.btnIKnowIt.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            binding.cardFlashcard.rotationY = 0f
            viewModel.markFlashcardKnown()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flashcardState.collect { state ->
                    renderFlashcardState(state)
                }
            }
        }
    }

    private fun flipCard3D() {
        if (isFlipping) return
        val b = _binding ?: return

        if (!AppPreferences.areAnimationsEnabled(requireContext())) {
            viewModel.toggleFlashcardSide()
            return
        }

        isFlipping = true
        b.cardFlashcard.animate()
            .rotationY(90f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(140L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                val bindingNow = _binding
                if (bindingNow != null) {
                    viewModel.toggleFlashcardSide()
                    bindingNow.cardFlashcard.rotationY = -90f
                    bindingNow.cardFlashcard.animate()
                        .rotationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160L)
                        .setInterpolator(DecelerateInterpolator())
                        .withEndAction { isFlipping = false }
                        .start()
                } else {
                    isFlipping = false
                }
            }
            .start()
    }

    private fun renderFlashcardState(state: FlashcardUiState) {
        val b = _binding ?: return
        if (state.isEmpty) {
            b.tvEmptyFlashcards.visibility = View.VISIBLE
            b.flashcardContent.visibility = View.GONE
            return
        }

        b.tvEmptyFlashcards.visibility = View.GONE
        b.flashcardContent.visibility = View.VISIBLE

        b.tvFlashcardProgress.text = getString(
            R.string.flashcards_progress,
            state.currentCardIndex,
            state.totalCardsCount,
            state.knownCount
        )

        if (!state.isShowingBack) {
            b.tvCardSideLabel.text = getString(R.string.flashcards_front_label)
            b.tvCardMainText.text = state.currentWord
            b.btnSpeakCard.visibility = View.VISIBLE
        } else {
            b.tvCardSideLabel.text = getString(R.string.flashcards_back_label)
            b.tvCardMainText.text = if (state.currentNote.isNotEmpty()) {
                state.currentNote
            } else {
                getString(R.string.flashcards_no_note)
            }
            b.btnSpeakCard.visibility = View.INVISIBLE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
