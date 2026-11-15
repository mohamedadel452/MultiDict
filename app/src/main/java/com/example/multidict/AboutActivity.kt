package com.example.multidict

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.multidict.databinding.ActivityAboutBinding

/**
 * Phase 7: Simple, scholarly "About" screen ("Ink & Ivory" design system).
 * - Reads app version dynamically from PackageManager
 * - Displays "What is this app?", "Made with love by Eng. Mohamed Adel", and "A free gift" cards
 * - Staggered 250ms fade-in + slide-up entrance animation (respects system "Remove animations")
 * - "Share the app" action using the Android share sheet
 * - "Rate the app" button ready to enable once published on Google Play
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppPreferences.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppearancePrefs.syncSystemBarIconColors(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.aboutRoot) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            windowInsets
        }

        binding.btnBackAbout.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            onBackPressedDispatcher.onBackPressed()
        }

        // 1. Read versionName dynamically from PackageManager
        val versionName = try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            pkgInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
        binding.tvAboutVersion.text = getString(R.string.about_version_format, versionName)

        // 2. "Share the app" button — opens Android Share Sheet with app name & short message
        binding.btnShareApp.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.about_share_message))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.about_btn_share)))
        }

        // 3. "Rate the app" button (hidden for now until the app is published on Google Play).
        // HOW TO ENABLE ONCE PUBLISHED:
        // Change `binding.btnRateApp.visibility = View.VISIBLE` below (or in activity_about.xml),
        // and optionally append the Play Store URL to `R.string.about_share_message`.
        binding.btnRateApp.visibility = View.GONE
        binding.btnRateApp.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                )
            } catch (_: Exception) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    )
                )
            }
        }

        runStaggeredEntranceAnimation()
    }

    /**
     * Gentle fade-in and slight slide-up animation for the cards, staggered, 250ms each.
     * Respects the system "Remove animations" setting.
     */
    private fun runStaggeredEntranceAnimation() {
        val animatedSections = listOf<View>(
            binding.headerAbout,
            binding.cardAboutWhat,
            binding.cardAboutAuthor,
            binding.cardAboutGift,
            binding.tvAboutClosing,
            binding.layoutAboutActions,
            binding.tvAboutFooter
        )

        if (!AppPreferences.areAnimationsEnabled(this)) {
            for (section in animatedSections) {
                section.alpha = 1f
                section.translationY = 0f
            }
            return
        }

        val slideOffsetPx = 16f * resources.displayMetrics.density
        val interpolator = FastOutSlowInInterpolator()

        animatedSections.forEachIndexed { index, section ->
            section.alpha = 0f
            section.translationY = slideOffsetPx
            section.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(250L)
                .setInterpolator(interpolator)
                .start()
        }
    }
}
