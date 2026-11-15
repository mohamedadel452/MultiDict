package com.example.multidict

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.multidict.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * "Einstellungen / Settings" Fragment:
 * - Appearance ("المظهر"):
 *   1. Language ("اللغة"): System default / English / العربية
 *   2. Theme ("السمة"): System default / Light / Dark (radio dialog + segmented preview buttons)
 *   3. Use system colors (optional Android 12+ Material You dynamic color)
 * - Wort automatisch entfernen
 * - Load pages in desktop mode (default off)
 * - Auto-search when switching tabs (default on)
 * - Vibrieren auf Klick
 * - Replay onboarding & tour button
 * - About the app row
 * - Clear cookies and cache button
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var suppressToggleCallback: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ctx = requireContext()

        // 1. Update Language & Theme subtitles + segmented button state
        refreshAppearanceSummary()

        binding.rowLanguage.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            showLanguageSelectionDialog()
        }

        binding.rowTheme.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            showThemeSelectionDialog()
        }

        binding.toggleThemeGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || suppressToggleCallback) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            applySelectedThemeMode(newMode, group)
        }

        // 2. Switches setup
        binding.cbDynamicColor.isChecked = AppearancePrefs.isDynamicColorEnabled(ctx)
        binding.cbAutoRemoveWord.isChecked = AppPreferences.isAutoRemoveWordEnabled(ctx)
        binding.cbDesktopMode.isChecked = AppPreferences.isDesktopModeEnabled(ctx)
        binding.cbAutoSearchTabs.isChecked = AppPreferences.isAutoSearchTabsEnabled(ctx)
        binding.cbVibrateOnClick.isChecked = AppPreferences.isVibrateOnClickEnabled(ctx)

        binding.btnBackSettings.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            parentFragmentManager.popBackStack()
        }

        binding.rowDynamicColor.setOnClickListener {
            val newState = !binding.cbDynamicColor.isChecked
            binding.cbDynamicColor.isChecked = newState
            AppearancePrefs.setDynamicColorEnabled(requireContext(), newState)
            AppPreferences.vibrateIfEnabled(it)
            activity?.recreate()
        }

        binding.rowAutoRemoveWord.setOnClickListener {
            val newState = !binding.cbAutoRemoveWord.isChecked
            binding.cbAutoRemoveWord.isChecked = newState
            AppPreferences.setAutoRemoveWordEnabled(requireContext(), newState)
            AppPreferences.vibrateIfEnabled(it)
        }

        binding.rowDesktopMode.setOnClickListener {
            val newState = !binding.cbDesktopMode.isChecked
            binding.cbDesktopMode.isChecked = newState
            AppPreferences.setDesktopModeEnabled(requireContext(), newState)
            AppPreferences.vibrateIfEnabled(it)
            (activity as? MainActivity)?.onDesktopModeChanged()
        }

        binding.rowAutoSearchTabs.setOnClickListener {
            val newState = !binding.cbAutoSearchTabs.isChecked
            binding.cbAutoSearchTabs.isChecked = newState
            AppPreferences.setAutoSearchTabsEnabled(requireContext(), newState)
            AppPreferences.vibrateIfEnabled(it)
        }

        binding.rowVibrateOnClick.setOnClickListener {
            val newState = !binding.cbVibrateOnClick.isChecked
            binding.cbVibrateOnClick.isChecked = newState
            AppPreferences.setVibrateOnClickEnabled(requireContext(), newState)
            AppPreferences.vibrateIfEnabled(it)
        }

        binding.rowAboutApp.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }

        binding.btnClearCookiesCache.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            (activity as? MainActivity)?.clearWebViewCookiesAndCache()
            Toast.makeText(requireContext(), R.string.cookies_cache_cleared, Toast.LENGTH_SHORT).show()
        }

        binding.btnReplayOnboarding.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            (activity as? MainActivity)?.replayOnboardingAndTour()
        }
    }

    private fun refreshAppearanceSummary() {
        val ctx = context ?: return
        val b = _binding ?: return

        val langLabel = AppearancePrefs.getLanguageDisplayLabel(ctx)
        b.tvLanguageValue.text = getString(R.string.settings_language_sub_format, langLabel)

        val themeMode = AppearancePrefs.getThemeMode(ctx)
        val themeLabel = AppearancePrefs.getThemeDisplayLabel(ctx)
        b.tvThemeValue.text = getString(R.string.settings_theme_sub_format, themeLabel)

        val previewIconRes = when (themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.drawable.ic_theme_light
            AppCompatDelegate.MODE_NIGHT_YES -> R.drawable.ic_theme_dark
            else -> R.drawable.ic_theme_system
        }
        b.ivThemePreview.setImageResource(previewIconRes)

        val checkedButtonId = when (themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.btnThemeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.btnThemeDark
            else -> R.id.btnThemeSystem
        }
        suppressToggleCallback = true
        b.toggleThemeGroup.check(checkedButtonId)
        suppressToggleCallback = false
    }

    private fun showLanguageSelectionDialog() {
        val ctx = requireContext()
        val options = arrayOf(
            getString(R.string.lang_system),
            getString(R.string.lang_english),
            getString(R.string.lang_arabic)
        )
        val tags = arrayOf(
            AppearancePrefs.LANG_SYSTEM,
            AppearancePrefs.LANG_EN,
            AppearancePrefs.LANG_AR
        )
        val currentTag = AppearancePrefs.getLanguageTag(ctx)
        val checkedIndex = tags.indexOf(currentTag).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                val selectedTag = tags.getOrElse(which) { AppearancePrefs.LANG_SYSTEM }
                dialog.dismiss()
                if (selectedTag != currentTag) {
                    AppearancePrefs.setLanguageTag(ctx, selectedTag)
                    refreshAppearanceSummary()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showThemeSelectionDialog() {
        val ctx = requireContext()
        val options = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        val modes = intArrayOf(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES
        )
        val currentMode = AppearancePrefs.getThemeMode(ctx)
        val checkedIndex = modes.indexOf(currentMode).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                val selectedMode = modes.getOrElse(which) { AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }
                dialog.dismiss()
                applySelectedThemeMode(selectedMode, binding.rowTheme)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applySelectedThemeMode(newMode: Int, feedbackView: View) {
        val ctx = context ?: return
        if (newMode != AppearancePrefs.getThemeMode(ctx)) {
            AppPreferences.vibrateIfEnabled(feedbackView)
            AppearancePrefs.setThemeMode(ctx, newMode)
            refreshAppearanceSummary()
            (activity as? MainActivity)?.onThemeChanged()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
