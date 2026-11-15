package com.example.multidict

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.multidict.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabAdapter: DictionaryAdapter
    private lateinit var pagerAdapter: DictionaryPagerAdapter
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val dictionaries = mutableListOf<Dictionary>()
    private var currentWord: String = ""
    private var selectedIndex: Int = 0
    private var suppressSuggestions: Boolean = false
    private var lastPromptedClipboardWord: String = ""

    private var tts: TextToSpeech? = null
    private var isGermanTtsReady: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoSearchRunnable: Runnable? = null
    private var suggestionJob: Job? = null

    companion object {
        private const val PREFS_NAME = "multidict_prefs"
        private const val KEY_LAST_WORD = "last_word"
        private const val KEY_LAST_DICT_INDEX = "last_dict_index"
        private const val SUGGESTION_DEBOUNCE_MS = 300L
        private const val AUTO_SEARCH_DELAY_MS = 650L
        private const val SAMPLE_WORD = "Haus"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        AppPreferences.applySavedTheme(this)
        super.onCreate(savedInstanceState)

        // Part A & D: Route to OnboardingActivity on first launch with zero flicker
        if (!OnboardingPrefs.isOnboardingCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppearancePrefs.syncSystemBarIconColors(this)
        setupEdgeToEdgeInsets()

        dictionaries.clear()
        dictionaries.addAll(AppPreferences.getOrderedDictionaries(this))

        restoreState(savedInstanceState)
        tts = TextToSpeech(this, this)

        setupToolbar()
        setupSuggestionsList()
        setupSearchRow()
        setupBottomTabs()
        setupViewPager()
        setupBackNavigation()

        handleIncomingIntent(intent)
    }

    private fun setupEdgeToEdgeInsets() {
        val baseBottomMargin = resources.getDimensionPixelSize(R.dimen.space_8)
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainRoot) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(bars.bottom, ime.bottom)

            binding.toolbar.updatePadding(top = bars.top)
            binding.overlayFragmentContainer.updatePadding(top = bars.top, bottom = bars.bottom)
            binding.bottomBarCard.updateLayoutParams< androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = baseBottomMargin + bottomInset
            }
            windowInsets
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized &&
            OnboardingPrefs.isOnboardingCompleted(this) &&
            !OnboardingPrefs.isCoachMarksCompleted(this) &&
            binding.coachMarkOverlay.visibility != View.VISIBLE
        ) {
            startCoachMarksTourIfNeeded()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            setIntent(intent)
            handleIncomingIntent(intent)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::binding.isInitialized && binding.coachMarkOverlay.visibility != View.VISIBLE) {
            checkClipboardForSearch()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.GERMAN)
            isGermanTtsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            isGermanTtsReady = false
        }
    }

    fun speakWord(word: String) {
        if (word.isBlank()) {
            Toast.makeText(this, R.string.tts_no_word, Toast.LENGTH_SHORT).show()
            return
        }
        val engine = tts
        if (engine == null || !isGermanTtsReady) {
            Toast.makeText(this, R.string.tts_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        val langResult = engine.setLanguage(Locale.GERMAN)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, R.string.tts_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        engine.speak(word, TextToSpeech.QUEUE_FLUSH, null, "multidict_pronounce")
    }

    /**
     * Handles text received via text selection ("ACTION_PROCESS_TEXT") or Share ("ACTION_SEND").
     */
    private fun handleIncomingIntent(incomingIntent: Intent?) {
        val action = incomingIntent?.action ?: return
        val extractedText: String? = when (action) {
            Intent.ACTION_PROCESS_TEXT -> {
                incomingIntent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
            Intent.ACTION_SEND -> {
                if (incomingIntent.type?.startsWith("text/") == true) {
                    incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
                } else {
                    null
                }
            }
            else -> null
        }

        val cleaned = extractedText?.trim().orEmpty()
        if (cleaned.isNotEmpty()) {
            incomingIntent.action = Intent.ACTION_MAIN
            searchWordFromList(cleaned)
        }
    }

    /**
     * Checks clipboard when coming to foreground: if clipboard has a short text (<= 30 chars, 1-2 words)
     * different from [currentWord], prompts with a floating Snackbar "Search for '<word>'?".
     */
    private fun checkClipboardForSearch() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            if (!clipboard.hasPrimaryClip()) return
            val clipText = clipboard.primaryClip
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
                .orEmpty()

            if (clipText.isEmpty() || clipText.length > 30) return
            if (clipText.startsWith("http://", ignoreCase = true) ||
                clipText.startsWith("https://", ignoreCase = true)
            ) return

            val words = clipText.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size !in 1..2) return

            if (clipText.equals(currentWord, ignoreCase = true) ||
                clipText.equals(lastPromptedClipboardWord, ignoreCase = true)
            ) return

            lastPromptedClipboardWord = clipText
            Snackbar.make(
                binding.root,
                getString(R.string.clipboard_search_prompt, clipText),
                Snackbar.LENGTH_LONG
            ).setAnchorView(binding.bottomBarCard)
                .setAction(R.string.search_action) {
                    searchWordFromList(clipText)
                }.show()
        } catch (_: Exception) {
            // Ignore clipboard access exceptions
        }
    }

    fun getCurrentWord(): String = currentWord

    /**
     * Called when a word is tapped in [HistoryFragment], [FavoritesFragment], Clipboard Snackbar, or external Intent.
     */
    fun searchWordFromList(word: String) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return

        suppressSuggestions = true
        binding.etSearch.setText(trimmed)
        binding.etSearch.setSelection(trimmed.length)
        suppressSuggestions = false
        updateClearButtonVisibility(trimmed.isNotEmpty())

        hideSuggestions()
        performSearch(hideSoftKeyboard = true)
    }

    /**
     * Called by [DictionariesOrderFragment] whenever the user reorders, enables/disables, or adds a dictionary.
     */
    fun onDictionaryOrderChanged() {
        val currentDictName = dictionaries.getOrNull(selectedIndex)?.name
        val updatedList = AppPreferences.getOrderedDictionaries(this)

        dictionaries.clear()
        dictionaries.addAll(updatedList)

        val newSelectedIdx = dictionaries.indexOfFirst { it.name == currentDictName }
            .takeIf { it >= 0 } ?: 0
        selectedIndex = newSelectedIdx
        savePreferences()

        binding.viewPager.offscreenPageLimit = dictionaries.size.coerceAtLeast(1)
        pagerAdapter.notifyDataSetChanged()
        tabAdapter.updateItems(dictionaries.toList(), selectedIndex)
        binding.viewPager.setCurrentItem(selectedIndex, false)
        scrollToSelectedTab(smooth = false)
        binding.viewPager.post {
            loadWordInAllFragments()
        }
    }

    /**
     * Called by [SettingsFragment] when "Load pages in desktop mode" is toggled.
     */
    fun onDesktopModeChanged() {
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is DictionaryFragment) {
                fragment.applyDesktopModeAndReload()
            }
        }
    }

    /**
     * Called by [SettingsFragment] when the app theme changes so WebViews and system bar icons stay in sync.
     */
    fun onThemeChanged() {
        AppearancePrefs.syncSystemBarIconColors(this)
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is DictionaryFragment) {
                fragment.applyWebViewThemeMode()
            }
        }
    }

    /**
     * Clears WebView cookies, DOM storage, and cache across all dictionary WebViews.
     */
    fun clearWebViewCookiesAndCache() {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
            for (fragment in supportFragmentManager.fragments) {
                if (fragment is DictionaryFragment) {
                    fragment.clearCache()
                }
            }
        } catch (_: Exception) {
            // Ignore WebView storage cleanup exceptions
        }
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (savedInstanceState != null) {
            currentWord = savedInstanceState.getString(KEY_LAST_WORD, "") ?: ""
            selectedIndex = savedInstanceState.getInt(KEY_LAST_DICT_INDEX, 0)
        } else {
            currentWord = prefs.getString(KEY_LAST_WORD, "") ?: ""
            selectedIndex = prefs.getInt(KEY_LAST_DICT_INDEX, 0)
        }
        selectedIndex = selectedIndex.coerceIn(0, (dictionaries.size - 1).coerceAtLeast(0))
    }

    private fun savePreferences() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_WORD, currentWord)
            .putInt(KEY_LAST_DICT_INDEX, selectedIndex)
            .apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_LAST_WORD, currentWord)
        outState.putInt(KEY_LAST_DICT_INDEX, selectedIndex)
    }

    private fun setupToolbar() {
        updateToolbarTitle()
        updateFavoriteIcon()

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_speak -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    speakWord(currentWord)
                    true
                }
                R.id.action_toggle_favorite -> {
                    if (currentWord.isNotBlank()) {
                        if (AppPreferences.isVibrateOnClickEnabled(this)) {
                            binding.toolbar.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        AppPreferences.toggleFavorite(this, currentWord)
                        updateFavoriteIcon()
                    }
                    true
                }
                R.id.action_history -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    openOverlayFragment(HistoryFragment(), "history")
                    true
                }
                R.id.action_favorites -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    openOverlayFragment(FavoritesFragment(), "favorites")
                    true
                }
                R.id.action_flashcards -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    openOverlayFragment(FlashcardsFragment(), "flashcards")
                    true
                }
                R.id.action_dictionaries -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    openOverlayFragment(DictionariesOrderFragment(), "dictionaries_order")
                    true
                }
                R.id.action_open_browser -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    openCurrentPageInBrowser()
                    true
                }
                R.id.action_how_to_use -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    replayOnboardingAndTour()
                    true
                }
                R.id.action_about -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                R.id.action_settings -> {
                    AppPreferences.vibrateIfEnabled(binding.toolbar)
                    openOverlayFragment(SettingsFragment(), "settings")
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Replays the full 4-page Onboarding carousel + 4-step interactive Coach Mark tour on MainActivity.
     */
    fun replayOnboardingAndTour() {
        hideKeyboard()
        hideSuggestions()
        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        OnboardingPrefs.resetForReplay(this)
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
    }

    /**
     * Starts or resumes the 4-step interactive Coach Mark tour over MainActivity.
     */
    private fun startCoachMarksTourIfNeeded() {
        binding.toolbar.post {
            if (isDestroyed || !::binding.isInitialized || OnboardingPrefs.isCoachMarksCompleted(this)) {
                return@post
            }
            hideKeyboard()
            hideSuggestions()

            val steps = listOf(
                CoachMarkStep(
                    targetProvider = { binding.searchRow },
                    title = getString(R.string.coach_title_1),
                    description = getString(R.string.coach_desc_1)
                ),
                CoachMarkStep(
                    targetProvider = { binding.bottomBarCard },
                    title = getString(R.string.coach_title_2),
                    description = getString(R.string.coach_desc_2)
                ),
                CoachMarkStep(
                    targetProvider = { binding.toolbar.findViewById(R.id.action_toggle_favorite) },
                    title = getString(R.string.coach_title_3),
                    description = getString(R.string.coach_desc_3)
                ),
                CoachMarkStep(
                    targetProvider = { binding.toolbar.findViewById(R.id.action_speak) },
                    title = getString(R.string.coach_title_4),
                    description = getString(R.string.coach_desc_4)
                )
            )

            val savedStep = OnboardingPrefs.getCoachMarkStep(this)
            binding.coachMarkOverlay.startTour(
                tourSteps = steps,
                startIndex = savedStep,
                onFinished = {
                    runSmartSampleWordSearch()
                }
            )
        }
    }

    /**
     * Part C: At the end of the tour, automatically fills the search box with "Haus",
     * runs the search across all dictionaries, and shows a guiding Snackbar.
     */
    private fun runSmartSampleWordSearch() {
        suppressSuggestions = true
        binding.etSearch.setText(SAMPLE_WORD)
        binding.etSearch.setSelection(SAMPLE_WORD.length)
        suppressSuggestions = false
        updateClearButtonVisibility(true)

        currentWord = SAMPLE_WORD
        AppPreferences.addHistoryWord(this, SAMPLE_WORD)
        updateToolbarTitle()
        updateFavoriteIcon()
        savePreferences()
        loadWordInAllFragments()

        Snackbar.make(
            binding.root,
            getString(R.string.sample_word_snackbar),
            Snackbar.LENGTH_LONG
        ).setAnchorView(binding.bottomBarCard).show()
    }

    private fun openCurrentPageInBrowser() {
        val activeFragment = findDictionaryFragment(selectedIndex)
        val url = activeFragment?.getCurrentUrl().takeIf { !it.isNullOrBlank() }
            ?: dictionaries.getOrNull(selectedIndex)?.let { DictionaryRepository.buildUrl(it, currentWord) }
            ?: return

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            // Ignore if no browser is installed
        }
    }

    fun updateFavoriteIcon() {
        val starItem = binding.toolbar.menu.findItem(R.id.action_toggle_favorite) ?: return
        val isFav = currentWord.isNotBlank() && AppPreferences.isFavorite(this, currentWord)
        starItem.setIcon(
            if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_border
        )
    }

    private fun openOverlayFragment(fragment: Fragment, tag: String) {
        hideKeyboard()
        hideSuggestions()
        if (AppPreferences.areAnimationsEnabled(this)) {
            fragment.enterTransition = MaterialFadeThrough().apply { duration = 250L }
            fragment.exitTransition = MaterialFadeThrough().apply { duration = 200L }
        }
        supportFragmentManager.popBackStack(
            null,
            androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.overlayFragmentContainer, fragment, tag)
            .addToBackStack(tag)
            .commit()
    }

    private fun updateToolbarTitle() {
        val displayTitle = if (currentWord.isNotEmpty()) {
            currentWord
        } else {
            getString(R.string.app_name)
        }
        binding.tvToolbarWord.text = displayTitle
    }

    private fun setupSuggestionsList() {
        suggestionAdapter = SuggestionAdapter { selectedSuggestion ->
            suppressSuggestions = true
            binding.etSearch.setText(selectedSuggestion)
            binding.etSearch.setSelection(selectedSuggestion.length)
            suppressSuggestions = false
            updateClearButtonVisibility(selectedSuggestion.isNotEmpty())

            hideSuggestions()
            performSearch(hideSoftKeyboard = true)
        }
        binding.rvSuggestions.layoutManager = LinearLayoutManager(this)
        binding.rvSuggestions.adapter = suggestionAdapter
    }

    private fun showSuggestionsCard() {
        if (binding.cardSuggestions.visibility == View.VISIBLE) return
        binding.cardSuggestions.visibility = View.VISIBLE
        if (AppPreferences.areAnimationsEnabled(this)) {
            binding.cardSuggestions.alpha = 0f
            binding.cardSuggestions.translationY = -8f * resources.displayMetrics.density
            binding.cardSuggestions.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180L)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            binding.cardSuggestions.alpha = 1f
            binding.cardSuggestions.translationY = 0f
        }
    }

    private fun hideSuggestions() {
        suggestionJob?.cancel()
        suggestionAdapter.clear()
        binding.cardSuggestions.visibility = View.GONE
    }

    private fun updateClearButtonVisibility(hasText: Boolean) {
        if (hasText) {
            if (binding.btnClear.visibility != View.VISIBLE) {
                binding.btnClear.alpha = 0f
                binding.btnClear.visibility = View.VISIBLE
                binding.btnClear.animate().alpha(1f).setDuration(150L).start()
            }
        } else {
            binding.btnClear.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSearchRow() {
        suppressSuggestions = true
        binding.etSearch.setText(currentWord)
        binding.etSearch.setSelection(binding.etSearch.text.length)
        suppressSuggestions = false
        updateClearButtonVisibility(currentWord.isNotEmpty())

        // Focus ring animation on search pill
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            binding.searchRow.setBackgroundResource(
                if (hasFocus) R.drawable.bg_search_input_focused else R.drawable.bg_search_input
            )
        }

        // Support "Wort automatisch entfernen" when user taps the search field to type a new word
        binding.etSearch.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (!binding.etSearch.hasFocus() &&
                    AppPreferences.isAutoRemoveWordEnabled(this) &&
                    binding.etSearch.text.isNotEmpty()
                ) {
                    autoSearchRunnable?.let { mainHandler.removeCallbacks(it) }
                    suppressSuggestions = true
                    binding.etSearch.text.clear()
                    suppressSuggestions = false
                    updateClearButtonVisibility(false)
                    hideSuggestions()
                }
            }
            false
        }

        // Handle 300ms debounced Wiktionary autocomplete suggestions + background fragment search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val rawText = s?.toString().orEmpty()
                updateClearButtonVisibility(rawText.isNotEmpty())
                if (suppressSuggestions) return

                val typed = rawText.trim()

                // 1. Autocomplete suggestions (debounce 300ms, min 2 chars)
                suggestionJob?.cancel()
                if (typed.length >= 2 && binding.etSearch.hasFocus()) {
                    suggestionJob = lifecycleScope.launch {
                        delay(SUGGESTION_DEBOUNCE_MS)
                        val suggestions = WiktionarySuggestionClient.fetchSuggestions(typed)
                        if (suggestions.isNotEmpty() && binding.etSearch.hasFocus()) {
                            suggestionAdapter.submitList(suggestions, typed)
                            showSuggestionsCard()
                        } else {
                            binding.cardSuggestions.visibility = View.GONE
                        }
                    }
                } else {
                    binding.cardSuggestions.visibility = View.GONE
                }

                // 2. Background fragment search while typing
                if (typed == currentWord) return
                autoSearchRunnable?.let { mainHandler.removeCallbacks(it) }
                if (typed.isNotEmpty()) {
                    val runnable = Runnable {
                        if (!isDestroyed && typed != currentWord) {
                            currentWord = typed
                            updateToolbarTitle()
                            updateFavoriteIcon()
                            savePreferences()
                            if (AppPreferences.isAutoSearchTabsEnabled(this@MainActivity)) {
                                loadWordInAllFragments()
                            } else {
                                findDictionaryFragment(selectedIndex)?.ensureWordLoaded(currentWord)
                            }
                        }
                    }
                    autoSearchRunnable = runnable
                    mainHandler.postDelayed(runnable, AUTO_SEARCH_DELAY_MS)
                }
            }
        })

        // Trigger immediate search from keyboard IME search/enter action
        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            val isEnterKey = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                isEnterKey
            ) {
                performSearch(hideSoftKeyboard = true)
                true
            } else {
                false
            }
        }

        // Circular primary search button inside pill triggers immediate search
        binding.btnSearch.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            performSearch(hideSoftKeyboard = true)
        }

        // Clear (X) button empties the input field and focuses it
        binding.btnClear.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            autoSearchRunnable?.let { mainHandler.removeCallbacks(it) }
            suppressSuggestions = true
            binding.etSearch.text.clear()
            suppressSuggestions = false
            updateClearButtonVisibility(false)
            hideSuggestions()
            binding.etSearch.requestFocus()
            showKeyboard(binding.etSearch)
        }
    }

    private fun performSearch(hideSoftKeyboard: Boolean) {
        autoSearchRunnable?.let { mainHandler.removeCallbacks(it) }
        hideSuggestions()

        val typedWord = binding.etSearch.text.toString().trim()
        if (typedWord.isNotEmpty()) {
            currentWord = typedWord
        }

        // Save to search history (newest first, no duplicates, max 200)
        if (currentWord.isNotEmpty()) {
            AppPreferences.addHistoryWord(this, currentWord)
        }

        updateToolbarTitle()
        updateFavoriteIcon()
        savePreferences()

        if (hideSoftKeyboard) {
            hideKeyboard()
            binding.etSearch.clearFocus()
            if (AppPreferences.isAutoRemoveWordEnabled(this)) {
                suppressSuggestions = true
                binding.etSearch.text.clear()
                suppressSuggestions = false
                updateClearButtonVisibility(false)
            }
        }

        if (AppPreferences.isAutoSearchTabsEnabled(this)) {
            loadWordInAllFragments()
        } else {
            findDictionaryFragment(selectedIndex)?.ensureWordLoaded(currentWord)
        }
    }

    /**
     * Triggers every created [DictionaryFragment] to load [currentWord] right away
     * in the background without waiting for the user to swipe to it.
     */
    private fun loadWordInAllFragments() {
        for (i in dictionaries.indices) {
            findDictionaryFragment(i)?.ensureWordLoaded(currentWord)
        }
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is DictionaryFragment) {
                fragment.ensureWordLoaded(currentWord)
            }
        }
    }

    private fun setupBottomTabs() {
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvDictionaries.layoutManager = layoutManager

        tabAdapter = DictionaryAdapter(dictionaries.toMutableList(), selectedIndex) { index, _ ->
            hideSuggestions()
            selectedIndex = index
            savePreferences()
            scrollToSelectedTab(smooth = true)
            if (binding.viewPager.currentItem != index) {
                binding.viewPager.setCurrentItem(index, true)
            }
        }
        binding.rvDictionaries.adapter = tabAdapter

        binding.rvDictionaries.post {
            scrollToSelectedTab(smooth = false)
        }
    }

    private fun setupViewPager() {
        pagerAdapter = DictionaryPagerAdapter(this, dictionaries)
        binding.viewPager.adapter = pagerAdapter
        // Keep ALL dictionary fragments created and alive in memory at all times
        binding.viewPager.offscreenPageLimit = dictionaries.size.coerceAtLeast(1)

        // Subtle crossfade transition when switching tabs
        binding.viewPager.setPageTransformer { page, position ->
            val absPos = abs(position)
            page.alpha = (1f - absPos * 0.25f).coerceIn(0.75f, 1f)
        }

        (binding.viewPager.getChildAt(0) as? RecyclerView)?.apply {
            setItemViewCacheSize(dictionaries.size)
            increaseViewPagerTouchSlop(this)
        }

        binding.viewPager.setCurrentItem(selectedIndex, false)

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                hideSuggestions()
                selectedIndex = position
                savePreferences()
                tabAdapter.selectPosition(position)
                scrollToSelectedTab(smooth = true)
                if (AppPreferences.isAutoSearchTabsEnabled(this@MainActivity)) {
                    findDictionaryFragment(position)?.ensureWordLoaded(currentWord)
                }
            }
        })

        binding.viewPager.post {
            if (AppPreferences.isAutoSearchTabsEnabled(this)) {
                loadWordInAllFragments()
            } else {
                findDictionaryFragment(selectedIndex)?.ensureWordLoaded(currentWord)
            }
        }
    }

    private fun findDictionaryFragment(index: Int): DictionaryFragment? {
        if (index !in dictionaries.indices) return null
        val itemId = pagerAdapter.getItemId(index)
        return supportFragmentManager.findFragmentByTag("f$itemId") as? DictionaryFragment
    }

    /**
     * Increases the internal RecyclerView touchSlop of ViewPager2 so slight diagonal finger tilts
     * never trigger an accidental tab switch.
     */
    private fun increaseViewPagerTouchSlop(recyclerView: RecyclerView) {
        try {
            val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val defaultTouchSlop = touchSlopField.get(recyclerView) as Int
            touchSlopField.set(recyclerView, (defaultTouchSlop * 2.5f).toInt())
        } catch (_: Exception) {
            // Ignore reflection fallback if field changes on future Android versions
        }
    }

    /**
     * Smoothly scrolls the bottom dictionary bar so the selected tab is centered when possible.
     */
    private fun scrollToSelectedTab(smooth: Boolean) {
        val lm = binding.rvDictionaries.layoutManager as? LinearLayoutManager ?: return
        binding.rvDictionaries.post {
            val targetView = lm.findViewByPosition(selectedIndex)
            if (targetView != null) {
                val parentCenter = binding.rvDictionaries.width / 2
                val childCenter = (targetView.left + targetView.right) / 2
                val dx = childCenter - parentCenter
                if (smooth && AppPreferences.areAnimationsEnabled(this)) {
                    binding.rvDictionaries.smoothScrollBy(dx, 0)
                } else {
                    binding.rvDictionaries.scrollBy(dx, 0)
                }
            } else {
                if (smooth && AppPreferences.areAnimationsEnabled(this)) {
                    binding.rvDictionaries.smoothScrollToPosition(selectedIndex)
                } else {
                    binding.rvDictionaries.scrollToPosition(selectedIndex)
                }
            }
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Close autocomplete suggestions list first if visible
                if (binding.cardSuggestions.visibility == View.VISIBLE) {
                    hideSuggestions()
                    return
                }
                // Close overlay fragment (History, Favorites, Flashcards, Wörterbücher, Einstellungen) if open
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    return
                }
                val activeFragment = findDictionaryFragment(selectedIndex)
                if (activeFragment != null && activeFragment.canGoBackInWebView()) {
                    activeFragment.goBackInWebView()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onDestroy() {
        autoSearchRunnable?.let { mainHandler.removeCallbacks(it) }
        suggestionJob?.cancel()
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        super.onDestroy()
    }
}
