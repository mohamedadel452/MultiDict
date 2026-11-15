package com.example.multidict.ui

import com.example.multidict.R
import com.example.multidict.model.*
import com.example.multidict.data.*
import com.example.multidict.viewmodel.*
import com.example.multidict.ui.*
import com.example.multidict.ui.adapter.*
import com.example.multidict.ui.widget.*

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.multidict.databinding.ActivityOnboardingBinding
import kotlin.math.abs

/**
 * First-run 4-page Welcome Carousel with custom indicator dots, parallax page transitions,
 * edge-to-edge insets, RTL support, and Skip / Next / Get started controls.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val pages = listOf(
        OnboardingPage(
            R.drawable.ic_onboard_1_search,
            R.string.onboard_title_1,
            R.string.onboard_desc_1
        ),
        OnboardingPage(
            R.drawable.ic_onboard_smart_ml,
            R.string.onboard_title_smart_ml,
            R.string.onboard_desc_smart_ml
        ),
        OnboardingPage(
            R.drawable.ic_onboard_2_tabs,
            R.string.onboard_title_2,
            R.string.onboard_desc_2
        ),
        OnboardingPage(
            R.drawable.ic_onboard_3_study,
            R.string.onboard_title_3,
            R.string.onboard_desc_3
        ),
        OnboardingPage(
            R.drawable.ic_onboard_4_ready,
            R.string.onboard_title_4,
            R.string.onboard_desc_4
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppearancePrefs.syncSystemBarIconColors(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            windowInsets
        }

        setupViewPager()
        setupControls()
        setupBackNavigation()
        updateIndicatorsAndButtons(binding.vpOnboarding.currentItem)
    }

    private fun setupViewPager() {
        binding.vpOnboarding.adapter = OnboardingAdapter(pages)

        // Subtle parallax and fade PageTransformer (supports LTR and RTL)
        binding.vpOnboarding.setPageTransformer { page, position ->
            val absPos = abs(position)
            page.alpha = (1f - absPos * 0.45f).coerceIn(0.3f, 1f)
            val illustration = page.findViewById<View>(R.id.ivOnboardingIllustration)
            illustration?.translationX = -position * (page.width * 0.2f)
            val scale = (1f - absPos * 0.08f).coerceIn(0.88f, 1f)
            illustration?.scaleX = scale
            illustration?.scaleY = scale
        }

        binding.vpOnboarding.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateIndicatorsAndButtons(position)
            }
        })
    }

    private fun setupControls() {
        binding.btnSkipOnboarding.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            finishOnboarding()
        }

        binding.btnNextOnboarding.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val current = binding.vpOnboarding.currentItem
            if (current < pages.lastIndex) {
                binding.vpOnboarding.setCurrentItem(current + 1, true)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updateIndicatorsAndButtons(activePosition: Int) {
        binding.dotsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val marginPx = (5 * density).toInt()

        for (i in pages.indices) {
            val dot = ImageView(this).apply {
                setImageResource(
                    if (i == activePosition) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = marginPx
                    marginEnd = marginPx
                }
            }
            binding.dotsContainer.addView(dot)
        }

        val isLastPage = activePosition == pages.lastIndex
        binding.btnSkipOnboarding.visibility = if (isLastPage) View.INVISIBLE else View.VISIBLE
        binding.btnNextOnboarding.setText(
            if (isLastPage) R.string.onboard_get_started else R.string.onboard_next
        )
    }

    private fun finishOnboarding() {
        OnboardingPrefs.setOnboardingCompleted(this, true)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = binding.vpOnboarding.currentItem
                if (current > 0) {
                    binding.vpOnboarding.setCurrentItem(current - 1, true)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
