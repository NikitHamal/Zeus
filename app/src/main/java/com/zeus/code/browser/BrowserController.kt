package com.zeus.code.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

@SuppressLint("SetJavaScriptEnabled")
class BrowserController(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true }

    var webView: WebView? = null
        private set

    private val _currentUrl = MutableStateFlow("about:blank")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _actionLogs = MutableStateFlow<List<String>>(emptyList())
    val actionLogs: StateFlow<List<String>> = _actionLogs.asStateFlow()

    private var pageLoadDeferred: CompletableDeferred<Boolean>? = null

    init {
        mainHandler.post {
            createWebView()
        }
    }

    private fun logAction(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _actionLogs.value = _actionLogs.value + "[$timestamp] $msg"
    }

    private fun createWebView() {
        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ZeusAgent/1.0"
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    _isLoading.value = true
                    _currentUrl.value = url.orEmpty()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    _isLoading.value = false
                    _currentUrl.value = url.orEmpty()
                    _pageTitle.value = view?.title.orEmpty()
                    pageLoadDeferred?.complete(true)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    _pageTitle.value = title.orEmpty()
                }
            }
        }
        webView = wv
    }

    suspend fun navigate(url: String): BrowserActionResult = withContext(Dispatchers.Main) {
        val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        logAction("Navigating to: $fullUrl")
        val deferred = CompletableDeferred<Boolean>()
        pageLoadDeferred = deferred

        webView?.loadUrl(fullUrl)
        val success = withTimeoutOrNull(25000) { deferred.await() } ?: false
        if (success) {
            logAction("Page loaded: ${_pageTitle.value}")
            BrowserActionResult(true, "Loaded: $fullUrl", _pageTitle.value)
        } else {
            logAction("Page navigation timed out or partial load.")
            BrowserActionResult(false, "Timeout loading $fullUrl")
        }
    }

    suspend fun evaluateJs(script: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        webView?.evaluateJavascript(script) { result ->
            deferred.complete(result ?: "null")
        }
        deferred.await()
    }

    suspend fun extractPageContent(): BrowserPageContent = withContext(Dispatchers.Main) {
        logAction("Extracting page text and DOM elements...")
        val script = """
            (function() {
                function getInteractiveElements() {
                    const elements = [];
                    const nodes = document.querySelectorAll('a, button, input, textarea, select, [role="button"]');
                    nodes.forEach((el, idx) => {
                        let text = (el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || '').trim();
                        if (text.length > 60) text = text.substring(0, 60) + '...';
                        let selector = el.id ? '#' + el.id : (el.name ? '[name="' + el.name + '"]' : el.tagName.toLowerCase() + ':nth-of-type(' + (idx + 1) + ')');
                        elements.push({
                            id: el.id || ('elem_' + idx),
                            tagName: el.tagName.toLowerCase(),
                            text: text,
                            href: el.href || '',
                            selector: selector,
                            isInteractive: true
                        });
                    });
                    return elements.slice(0, 100);
                }
                return JSON.stringify({
                    url: window.location.href,
                    title: document.title,
                    textContent: (document.body ? document.body.innerText.substring(0, 8000) : ''),
                    elements: getInteractiveElements()
                });
            })();
        """.trimIndent()

        val rawJson = evaluateJs(script)
        // WebView evaluateJavascript returns JSON-escaped string
        val cleaned = if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
            json.decodeFromString<String>(rawJson)
        } else {
            rawJson
        }

        try {
            json.decodeFromString<BrowserPageContent>(cleaned)
        } catch (_: Exception) {
            BrowserPageContent(
                url = _currentUrl.value,
                title = _pageTitle.value,
                textContent = "Could not parse page content",
                elements = emptyList()
            )
        }
    }

    suspend fun clickElement(selector: String): BrowserActionResult = withContext(Dispatchers.Main) {
        logAction("Clicking element: $selector")
        val escaped = selector.replace("'", "\\'")
        val script = """
            (function() {
                try {
                    const el = document.querySelector('$escaped');
                    if (el) {
                        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        el.click();
                        return 'ok';
                    }
                    return 'not_found';
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })();
        """.trimIndent()
        val result = evaluateJs(script).replace("\"", "")
        if (result == "ok") {
            logAction("Clicked: $selector")
            BrowserActionResult(true, "Clicked element $selector")
        } else {
            logAction("Failed to click: $selector ($result)")
            BrowserActionResult(false, "Element not found or error: $result")
        }
    }

    suspend fun typeText(selector: String, text: String): BrowserActionResult = withContext(Dispatchers.Main) {
        logAction("Typing text into: $selector")
        val escapedSelector = selector.replace("'", "\\'")
        val escapedText = text.replace("'", "\\'").replace("\n", "\\n")
        val script = """
            (function() {
                try {
                    const el = document.querySelector('$escapedSelector');
                    if (el) {
                        el.focus();
                        el.value = '$escapedText';
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        return 'ok';
                    }
                    return 'not_found';
                } catch(e) {
                    return 'error: ' + e.message;
                }
            })();
        """.trimIndent()
        val result = evaluateJs(script).replace("\"", "")
        if (result == "ok") {
            logAction("Typed into $selector")
            BrowserActionResult(true, "Entered text into $selector")
        } else {
            BrowserActionResult(false, "Failed to type: $result")
        }
    }

    suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Main) {
        logAction("Capturing browser screenshot...")
        val wv = webView ?: return@withContext null
        if (wv.width <= 0 || wv.height <= 0) return@withContext null
        val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        wv.draw(canvas)
        bitmap
    }
}
