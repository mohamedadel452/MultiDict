package com.example.multidict

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.example.multidict.databinding.FragmentDictionaryBinding

/**
 * Holds a persistent WebView for a single [Dictionary].
 * Immediately loads the active word as soon as the fragment view is created in the background,
 * with a soft skeleton shimmer placeholder on first load, ultra-thin gradient progress bar,
 * and automatic WebView dark mode support.
 */
class DictionaryFragment : Fragment() {

    private var _binding: FragmentDictionaryBinding? = null
    private val binding get() = _binding!!

    private var dictName: String = ""
    private var loadedWord: String? = null
    private var lastLoadedUrl: String = ""
    private var hasLoadError: Boolean = false
    private var hasCompletedInitialLoad: Boolean = false
    private var defaultUserAgent: String = ""
    private var shimmerAnimator: ValueAnimator? = null

    companion object {
        private const val ARG_DICT_NAME = "arg_dict_name"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        fun newInstance(dictName: String): DictionaryFragment {
            return DictionaryFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DICT_NAME, dictName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dictName = arguments?.getString(ARG_DICT_NAME).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDictionaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()

        binding.btnRetry.setOnClickListener {
            AppPreferences.vibrateIfEnabled(it)
            hideError()
            val dictionary = DictionaryRepository.findByName(requireContext(), dictName)
            val currentWord = (activity as? MainActivity)?.getCurrentWord().orEmpty()
            val targetUrl = lastLoadedUrl.ifEmpty {
                DictionaryRepository.buildUrl(dictionary, currentWord)
            }
            loadedWord = currentWord
            loadUrlInWebView(targetUrl)
        }

        val currentWord = (activity as? MainActivity)?.getCurrentWord().orEmpty()
        ensureWordLoaded(currentWord)
    }

    override fun onStart() {
        super.onStart()
        val ctx = context ?: return
        if (AppPreferences.isAutoSearchTabsEnabled(ctx)) {
            val currentWord = (activity as? MainActivity)?.getCurrentWord().orEmpty()
            ensureWordLoaded(currentWord)
        }
    }

    /**
     * Loads [word] in this dictionary's WebView ONLY if it isn't already displaying [word].
     */
    fun ensureWordLoaded(word: String) {
        val ctx = context ?: return
        if (_binding == null) return
        if (loadedWord == word && !hasLoadError) {
            return
        }
        loadedWord = word
        val dictionary = DictionaryRepository.findByName(ctx, dictName)
        val url = DictionaryRepository.buildUrl(dictionary, word)
        loadUrlInWebView(url)
    }

    fun applyDesktopModeAndReload() {
        val ctx = context ?: return
        val b = _binding ?: return
        val desktopMode = AppPreferences.isDesktopModeEnabled(ctx)
        b.webView.settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else defaultUserAgent
        b.webView.reload()
    }

    fun applyWebViewThemeMode() {
        val ctx = context ?: return
        val b = _binding ?: return
        val isNightMode = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            b.webView.settings.isAlgorithmicDarkeningAllowed = isNightMode
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            b.webView.settings.forceDark =
                if (isNightMode) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
        }
    }

    fun clearCache() {
        _binding?.webView?.clearCache(true)
    }

    fun getCurrentUrl(): String {
        val webUrl = _binding?.webView?.url
        if (!webUrl.isNullOrBlank()) return webUrl
        if (lastLoadedUrl.isNotBlank()) return lastLoadedUrl
        val ctx = context ?: return ""
        val dictionary = DictionaryRepository.findByName(ctx, dictName)
        val currentWord = (activity as? MainActivity)?.getCurrentWord().orEmpty()
        return DictionaryRepository.buildUrl(dictionary, currentWord)
    }

    fun canGoBackInWebView(): Boolean {
        return _binding?.webView?.canGoBack() == true
    }

    fun goBackInWebView() {
        val b = _binding ?: return
        hideError()
        b.webView.goBack()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val ctx = requireContext()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        defaultUserAgent = webView.settings.userAgentString
        val desktopMode = AppPreferences.isDesktopModeEnabled(ctx)
        val isNightMode = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            builtInZoomControls = true
            displayZoomControls = false
            userAgentString = if (desktopMode) DESKTOP_USER_AGENT else defaultUserAgent

            // Enable Android WebView dark mode properly when app is in night mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                isAlgorithmicDarkeningAllowed = isNightMode
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                forceDark = if (isNightMode) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                val b = _binding ?: return
                if (newProgress in 1..99) {
                    b.progressBar.animate().cancel()
                    b.progressBar.alpha = 1f
                    b.progressBar.visibility = View.VISIBLE
                    b.progressBar.progress = newProgress
                    if (newProgress >= 55 && b.shimmerLayout.visibility == View.VISIBLE) {
                        hideShimmer()
                    }
                } else {
                    fadeOutProgressBar()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return true

                if (scheme == "http" || scheme == "https") {
                    return false
                }

                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()))
                    startActivity(intent)
                } catch (_: Exception) {
                    // Ignore unsupported external schemes
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val b = _binding ?: return
                if (!url.isNullOrEmpty()) {
                    lastLoadedUrl = url
                }
                b.progressBar.animate().cancel()
                b.progressBar.alpha = 1f
                b.progressBar.visibility = View.VISIBLE
                b.progressBar.progress = 12
                if (!hasCompletedInitialLoad) {
                    showShimmer()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val b = _binding ?: return
                hasCompletedInitialLoad = true
                fadeOutProgressBar()
                hideShimmer()
                if (!hasLoadError) {
                    b.errorLayout.visibility = View.GONE
                    b.webView.visibility = View.VISIBLE
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showError()
                }
            }
        }
    }

    private fun showShimmer() {
        val b = _binding ?: return
        b.shimmerLayout.visibility = View.VISIBLE
        if (!AppPreferences.areAnimationsEnabled(requireContext())) {
            b.shimmerLayout.alpha = 0.85f
            return
        }
        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(0.45f, 0.95f).apply {
            duration = 750L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                _binding?.shimmerLayout?.alpha = anim.animatedValue as Float
            }
            start()
        }
    }

    private fun hideShimmer() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        val b = _binding ?: return
        if (b.shimmerLayout.visibility == View.VISIBLE) {
            b.shimmerLayout.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    _binding?.shimmerLayout?.visibility = View.GONE
                }
                .start()
        }
    }

    private fun fadeOutProgressBar() {
        val b = _binding ?: return
        if (b.progressBar.visibility != View.VISIBLE) return
        b.progressBar.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                _binding?.progressBar?.visibility = View.GONE
            }
            .start()
    }

    private fun loadUrlInWebView(url: String) {
        val b = _binding ?: return
        hasLoadError = false
        lastLoadedUrl = url
        hideError()
        b.webView.stopLoading()
        b.webView.loadUrl(url)
    }

    private fun showError() {
        val b = _binding ?: return
        hasLoadError = true
        hideShimmer()
        b.progressBar.visibility = View.GONE
        b.webView.visibility = View.GONE
        b.errorLayout.visibility = View.VISIBLE
    }

    private fun hideError() {
        val b = _binding ?: return
        hasLoadError = false
        b.errorLayout.visibility = View.GONE
        b.webView.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        _binding?.webView?.apply {
            (parent as? ViewGroup)?.removeView(this)
            stopLoading()
            settings.javaScriptEnabled = false
            clearHistory()
            removeAllViews()
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }
}
