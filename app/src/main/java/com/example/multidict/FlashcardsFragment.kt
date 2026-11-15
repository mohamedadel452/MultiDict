package com.example.multidict

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import com.example.multidict.databinding.FragmentFlashcardsBinding

/**
 * Flashcards practice screen for favorite words:
 * - Front: German word in Display serif + speaker button (reads word aloud via German TTS)
 * - Tap card to flip with 3D rotationY (cameraDistance set) -> Back: personal offline note/meaning
 * - "Review again" re-inserts the card near the front of the deck so it repeats more often
 * - "I know it" moves the card to the back of the deck and increments the known count
 */
class FlashcardsFragment : Fragment() {

    private var _binding: FragmentFlashcardsBinding? = null
    private val binding get() = _binding!!

    private val deck = ArrayDeque<String>()
    private var totalCardsCount: Int = 0
    private var currentCardIndex: Int = 1
    private var knownCount: Int = 0
    private var isShowingBack: Boolean = false
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

        val favorites = AppPreferences.getFavorites(requireContext())
        deck.clear()
        deck.addAll(favorites)
        totalCardsCount = favorites.size

        binding.btnBackFlashcards.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            parentFragmentManager.popBackStack()
        }

        if (deck.isEmpty()) {
            binding.tvEmptyFlashcards.visibility = View.VISIBLE
            binding.flashcardContent.visibility = View.GONE
            return
        }

        binding.tvEmptyFlashcards.visibility = View.GONE
        binding.flashcardContent.visibility = View.VISIBLE

        // Configure 3D camera distance for smooth perspective rotationY
        val scale = resources.displayMetrics.density
        binding.cardFlashcard.cameraDistance = 8000f * scale

        binding.cardFlashcard.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            flipCard()
        }

        binding.btnSpeakCard.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val word = deck.firstOrNull() ?: return@setOnClickListener
            (activity as? MainActivity)?.speakWord(word)
        }

        binding.btnReviewAgain.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val current = deck.removeFirstOrNull() ?: return@setOnClickListener
            val insertPos = minOf(2, deck.size)
            val tempList = deck.toMutableList()
            tempList.add(insertPos, current)
            deck.clear()
            deck.addAll(tempList)

            isShowingBack = false
            binding.cardFlashcard.rotationY = 0f
            renderCurrentCard()
        }

        binding.btnIKnowIt.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val current = deck.removeFirstOrNull() ?: return@setOnClickListener
            knownCount++
            currentCardIndex = (currentCardIndex % totalCardsCount) + 1
            deck.addLast(current)

            isShowingBack = false
            binding.cardFlashcard.rotationY = 0f
            renderCurrentCard()
        }

        renderCurrentCard()
    }

    private fun flipCard() {
        if (isFlipping) return
        val b = _binding ?: return
        isShowingBack = !isShowingBack

        if (!AppPreferences.areAnimationsEnabled(requireContext())) {
            renderCurrentCard()
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
                    renderCurrentCard()
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

    private fun renderCurrentCard() {
        val b = _binding ?: return
        val word = deck.firstOrNull() ?: return

        b.tvFlashcardProgress.text = getString(
            R.string.flashcards_progress,
            currentCardIndex,
            totalCardsCount,
            knownCount
        )

        if (!isShowingBack) {
            b.tvCardSideLabel.text = getString(R.string.flashcards_front_label)
            b.tvCardMainText.text = word
            b.btnSpeakCard.visibility = View.VISIBLE
        } else {
            b.tvCardSideLabel.text = getString(R.string.flashcards_back_label)
            val note = AppPreferences.getWordNote(requireContext(), word)
            b.tvCardMainText.text = if (note.isNotEmpty()) {
                note
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
