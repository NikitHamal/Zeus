package com.zeus.code.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@SuppressLint("SetJavaScriptEnabled")
class BrowserController(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    var webView: WebView? = null
        private set

    private val _currentUrl = MutableStateFlow("about:blank")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(false)
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    private val _actionLogs = MutableStateFlow<List<String>>(emptyList())
    val actionLogs: StateFlow<List<String>> = _actionLogs.asStateFlow()

    private var pageLoadDeferred: CompletableDeferred<Boolean>? = null

    companion object {
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 ZeusAgent/1.0"
        const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 ZeusAgent/1.0"
    }

    init {
        mainHandler.post {
            createWebView()
        }
    }

    fun logAction(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _actionLogs.value = (_actionLogs.value + "[$timestamp] $msg").takeLast(150)
    }

    private fun createWebView() {
        if (webView != null) return

        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = MOBILE_UA
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    _isLoading.value = true
                    _currentUrl.value = url.orEmpty()
                    _canGoBack.value = view?.canGoBack() ?: false
                    _canGoForward.value = view?.canGoForward() ?: false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    _isLoading.value = false
                    _currentUrl.value = url.orEmpty()
                    _pageTitle.value = view?.title.orEmpty()
                    _canGoBack.value = view?.canGoBack() ?: false
                    _canGoForward.value = view?.canGoForward() ?: false
                    pageLoadDeferred?.complete(true)
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    // Tolerates local / proxy SSL certificates for mobile web agents
                    handler?.proceed()
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    _pageTitle.value = title.orEmpty()
                }

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress >= 90) {
                        pageLoadDeferred?.complete(true)
                    }
                }
            }
        }
        webView = wv
    }

    suspend fun navigate(urlOrQuery: String): BrowserActionResult = withContext(Dispatchers.Main) {
        val trimmed = urlOrQuery.trim()
        val targetUrl = resolveNavigationUrl(trimmed)
        logAction("Navigating to: $targetUrl")

        val deferred = CompletableDeferred<Boolean>()
        pageLoadDeferred = deferred

        webView?.loadUrl(targetUrl)
        val loaded = withTimeoutOrNull(20000) { deferred.await() } ?: false
        if (loaded) {
            logAction("Loaded: ${_pageTitle.value} ($targetUrl)")
            BrowserActionResult(true, "Loaded: ${_pageTitle.value}", targetUrl)
        } else {
            logAction("Page navigation finished with timeout/partial load: $targetUrl")
            BrowserActionResult(true, "Navigated to: $targetUrl", targetUrl)
        }
    }

    suspend fun goBack(): BrowserActionResult = withContext(Dispatchers.Main) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
            logAction("Navigated back")
            BrowserActionResult(true, "Went back")
        } else {
            BrowserActionResult(false, "Cannot go back")
        }
    }

    suspend fun goForward(): BrowserActionResult = withContext(Dispatchers.Main) {
        if (webView?.canGoForward() == true) {
            webView?.goForward()
            logAction("Navigated forward")
            BrowserActionResult(true, "Went forward")
        } else {
            BrowserActionResult(false, "Cannot go forward")
        }
    }

    suspend fun reload(): BrowserActionResult = withContext(Dispatchers.Main) {
        webView?.reload()
        logAction("Reloaded page")
        BrowserActionResult(true, "Reloaded")
    }

    fun toggleDesktopMode(): Boolean {
        val newMode = !_isDesktopMode.value
        _isDesktopMode.value = newMode
        mainHandler.post {
            webView?.settings?.userAgentString = if (newMode) DESKTOP_UA else MOBILE_UA
            webView?.reload()
        }
        logAction(if (newMode) "Switched to Desktop Mode" else "Switched to Mobile Mode")
        return newMode
    }

    suspend fun evaluateJs(script: String): String = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String>()
        webView?.evaluateJavascript(script) { result ->
            deferred.complete(result ?: "null")
        }
        deferred.await()
    }

    /**
     * Extracts page content and assigns data-zeus-id to interactive elements.
     */
    suspend fun extractPageContent(): BrowserPageContent = withContext(Dispatchers.Main) {
        logAction("Inspecting DOM and extracting page content...")
        val script = """
            (function() {
                try {
                    // Tag and extract interactive elements
                    let counter = 0;
                    const elements = [];
                    const links = [];
                    
                    const query = 'a, button, input, textarea, select, [role="button"], [role="link"], [role="tab"], [onclick], [tabindex="0"]';
                    const nodes = document.querySelectorAll(query);
                    
                    nodes.forEach((el) => {
                        // Check visibility
                        const rect = el.getBoundingClientRect();
                        const style = window.getComputedStyle(el);
                        const isVisible = style.display !== 'none' && 
                                          style.visibility !== 'hidden' && 
                                          style.opacity !== '0' && 
                                          rect.width > 0 && 
                                          rect.height > 0;
                                          
                        if (!isVisible) return;
                        
                        // Assign or get stable zeusId
                        let zeusId = el.getAttribute('data-zeus-id');
                        if (!zeusId) {
                            zeusId = 'z-' + counter++;
                            el.setAttribute('data-zeus-id', zeusId);
                        }
                        
                        let text = (el.innerText || el.value || el.placeholder || el.getAttribute('aria-label') || el.title || '').trim();
                        if (text.length > 80) text = text.substring(0, 80) + '...';
                        
                        let selector = el.id ? '#' + el.id : (el.name ? '[name="' + el.name + '"]' : el.tagName.toLowerCase() + '[data-zeus-id="' + zeusId + '"]');
                        
                        const item = {
                            zeusId: zeusId,
                            tagName: el.tagName.toLowerCase(),
                            text: text,
                            href: el.href || '',
                            placeholder: el.placeholder || '',
                            ariaLabel: el.getAttribute('aria-label') || '',
                            inputType: el.type || '',
                            value: el.value || '',
                            selector: selector,
                            isInteractive: true,
                            isVisible: true,
                            bounds: {
                                left: rect.left,
                                top: rect.top,
                                right: rect.right,
                                bottom: rect.bottom,
                                width: rect.width,
                                height: rect.height
                            }
                        };
                        elements.push(item);
                        
                        if (el.tagName.toLowerCase() === 'a' && el.href) {
                            links.push({ text: text || el.href, url: el.href });
                        }
                    });
                    
                    // Extract text content cleanly
                    let textContent = '';
                    if (document.body) {
                        textContent = document.body.innerText.replace(/\s+/g, ' ').substring(0, 5000);
                    }
                    
                    return JSON.stringify({
                        url: window.location.href,
                        title: document.title || 'Untitled',
                        textContent: textContent,
                        elements: elements.slice(0, 60),
                        links: links.slice(0, 25)
                    });
                } catch(e) {
                    return JSON.stringify({
                        url: window.location.href,
                        title: document.title || 'Error',
                        textContent: 'Error extracting page: ' + e.message,
                        elements: [],
                        links: []
                    });
                }
            })();
        """.trimIndent()

        val rawJson = evaluateJs(script)
        val cleaned = unescapeJsString(rawJson)

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

    suspend fun clickElement(target: String): BrowserActionResult = withContext(Dispatchers.Main) {
        logAction("Clicking target: $target")
        val escaped = target.replace("'", "\\'")
        val script = """
            (function() {
                try {
                    let el = document.querySelector('[data-zeus-id="$escaped"]') || 
                             document.querySelector('$escaped') || 
                             document.getElementById('$escaped');
                             
                    // If not found, try text match
                    if (!el) {
                        const targetText = '$escaped'.toLowerCase();
                        const candidates = document.querySelectorAll('button, a, input[type="button"], input[type="submit"], [role="button"]');
                        for (let c of candidates) {
                            if ((c.innerText || c.value || '').trim().toLowerCase().includes(targetText)) {
                                el = c;
                                break;
                            }
                        }
                    }
                    
                    if (el) {
                        el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        
                        // Dispatch mouse events
                        el.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
                        el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                        el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
                        el.click();
                        
                        // If it's inside an anchor tag, trigger it
                        const parentLink = el.closest('a');
                        if (parentLink && parentLink.href) {
                            parentLink.click();
                        }
                        
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
            logAction("Clicked: $target")
            BrowserActionResult(true, "Clicked element $target")
        } else {
            logAction("Failed to click: $target ($result)")
            BrowserActionResult(false, "Element not found or error: $result")
        }
    }

    suspend fun typeText(target: String, text: String, submit: Boolean = false): BrowserActionResult = withContext(Dispatchers.Main) {
        logAction("Typing text into: $target")
        val escapedTarget = target.replace("'", "\\'")
        val escapedText = text.replace("'", "\\'").replace("\n", "\\n")
        val script = """
            (function() {
                try {
                    let el = document.querySelector('[data-zeus-id="$escapedTarget"]') || 
                             document.querySelector('$escapedTarget') || 
                             document.getElementById('$escapedTarget') ||
                             document.querySelector('input[type="text"], input[type="search"], input:not([type]), textarea');
                             
                    if (el) {
                        el.focus();
                        el.value = '$escapedText';
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        
                        if ($submit) {
                            const form = el.closest('form');
                            if (form) {
                                form.submit();
                            } else {
                                el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                                el.dispatchEvent(new KeyboardEvent('keypress', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                                el.dispatchEvent(new KeyboardEvent('keyup', { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                            }
                        }
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
            logAction("Entered text into $target")
            BrowserActionResult(true, "Entered text into $target")
        } else {
            logAction("Failed to type: $result")
            BrowserActionResult(false, "Failed to type: $result")
        }
    }

    suspend fun scroll(direction: String = "down", deltaY: Int = 400): BrowserActionResult = withContext(Dispatchers.Main) {
        val script = when (direction.lowercase()) {
            "up" -> "window.scrollBy({ top: -$deltaY, behavior: 'smooth' }); 'ok';"
            "down" -> "window.scrollBy({ top: $deltaY, behavior: 'smooth' }); 'ok';"
            "top" -> "window.scrollTo({ top: 0, behavior: 'smooth' }); 'ok';"
            "bottom" -> "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' }); 'ok';"
            else -> "window.scrollBy({ top: $deltaY, behavior: 'smooth' }); 'ok';"
        }
        evaluateJs(script)
        logAction("Scrolled $direction")
        BrowserActionResult(true, "Scrolled $direction")
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

    private fun resolveNavigationUrl(input: String): String {
        if (input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)) {
            return input
        }
        if (input.contains(".") && !input.contains(" ")) {
            return "https://$input"
        }
        val queryEncoded = URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
        return "https://html.duckduckgo.com/html/?q=$queryEncoded"
    }

    private fun unescapeJsString(raw: String): String {
        return if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length >= 2) {
            runCatching { json.decodeFromString<String>(raw) }.getOrDefault(raw)
        } else {
            raw
        }
    }
}
