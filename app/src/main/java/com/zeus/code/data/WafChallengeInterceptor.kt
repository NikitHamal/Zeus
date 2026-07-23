package com.zeus.code.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class WafChallengeInterceptor(private val context: Context) : Interceptor {

    companion object {
        private const val BASE_URL = "https://nebians.consica.com.np/"
        private const val BASE_HOST = "nebians.consica.com.np"
        private const val RETRY_HEADER = "X-Waf-Retried"
        private const val CHALLENGE_TIMEOUT_SECONDS = 35L
        private const val WEBVIEW_SETTLE_MS = 900L
        private const val MAX_SOLVE_ATTEMPTS = 2
        private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

        const val WAF_ERROR_MESSAGE = "NEBians server is temporarily protected by the hosting security filter. Please wait a few minutes and try again. If it keeps happening, switch networks or contact support."

        @Volatile
        private var cachedCookies: String? = null

        private val challengeLock = Any()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.url.host != BASE_HOST) return chain.proceed(originalRequest)

        var request = browserRequest(originalRequest)
        var response = chain.proceed(request)

        var attempt = originalRequest.header(RETRY_HEADER)?.toIntOrNull() ?: 0
        while (shouldSolve(originalRequest, response) && attempt < MAX_SOLVE_ATTEMPTS) {
            response.close()
            val solvedCookies = synchronized(challengeLock) {
                val existing = allCookiesFor(originalRequest)
                if (hasUsefulCookie(existing)) existing else solveChallengeInWebView(originalRequest.url.toString())
            }

            if (solvedCookies.isNullOrBlank()) {
                throw wafException(responseCode = 503)
            }

            cachedCookies = solvedCookies
            attempt += 1
            request = browserRequest(
                originalRequest.newBuilder()
                    .header(RETRY_HEADER, attempt.toString())
                    .header("Cookie", solvedCookies)
                    .build()
            )
            response = chain.proceed(request)
        }

        if (shouldSolve(originalRequest, response)) {
            response.close()
            throw wafException(responseCode = response.code)
        }

        if (isUnexpectedHtmlForApi(originalRequest, response)) {
            response.close()
            throw BackgroundAgentApiException(
                statusCode = response.code,
                errorCode = "unexpected_html",
                message = "The server returned an invalid response. Please check your internet connection and try again."
            )
        }

        return response
    }

    private fun browserRequest(request: Request): Request {
        val builder = request.newBuilder()
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Connection", "keep-alive")
            .header("Sec-Ch-Ua", "\"Chromium\";v=\"125\", \"Google Chrome\";v=\"125\", \"Not.A/Brand\";v=\"24\"")
            .header("Sec-Ch-Ua-Mobile", "?1")
            .header("Sec-Ch-Ua-Platform", "\"Android\"")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Referer", BASE_URL)

        val mergedCookies = mergeCookies(request.header("Cookie"), cachedCookies, allCookiesFor(request))
        if (mergedCookies.isNotBlank()) builder.header("Cookie", mergedCookies)
        return builder.build()
    }

    private fun shouldSolve(request: Request, response: Response): Boolean {
        val path = request.url.encodedPath
        val contentType = response.body?.contentType()?.toString().orEmpty()
        val likelyText = contentType.contains("text", true) ||
                contentType.contains("html", true) ||
                contentType.contains("json", true) ||
                contentType.isBlank()
        val protectedStatus = response.code in listOf(200, 403, 409, 429, 503, 520, 522, 524)
        if (!protectedStatus && !path.startsWith("/api/")) return false
        if (!likelyText && response.code !in listOf(403, 429, 503, 520, 522, 524)) return false

        val sample = try {
            response.peekBody(192 * 1024).string()
        } catch (_: Exception) {
            ""
        }
        if (isChallengeBody(sample)) return true
        if (response.code in listOf(403, 429, 503, 520, 522, 524) && !contentType.contains("application/json", true)) return true
        return path.startsWith("/api/") && response.code == 200 && contentType.contains("text/html", true)
    }

    private fun isUnexpectedHtmlForApi(request: Request, response: Response): Boolean {
        val contentType = response.body?.contentType()?.toString().orEmpty()
        if (!request.url.encodedPath.startsWith("/api/")) return false
        if (!contentType.contains("text/html", true)) return false
        val sample = try {
            response.peekBody(32 * 1024).string()
        } catch (_: Exception) {
            ""
        }
        return !isChallengeBody(sample)
    }

    private fun isChallengeBody(body: String): Boolean {
        if (body.isBlank()) return false
        val lower = body.lowercase(Locale.US)
        return lower.contains("imunify360") ||
                lower.contains("imunify") ||
                lower.contains("web shield") ||
                lower.contains("please wait") && lower.contains("moment") ||
                lower.contains("checking your browser") ||
                lower.contains("window.toshowcaptcha") ||
                lower.contains("captcha") && lower.contains("shield") ||
                lower.contains("human verification") ||
                lower.contains("security check") && lower.contains("browser")
    }

    private fun solveChallengeInWebView(targetUrl: String): String? {
        val result = AtomicReference<String?>(null)
        val finished = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val mainHandler = Handler(Looper.getMainLooper())
        val webViewRef = AtomicReference<WebView?>(null)

        fun finish(value: String?) {
            if (finished.compareAndSet(false, true)) {
                result.set(value)
                CookieManager.getInstance().flush()
                tryDestroyWebView(webViewRef.get())
                latch.countDown()
            }
        }

        mainHandler.post {
            try {
                val webView = WebView(context.applicationContext)
                webViewRef.set(webView)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    loadsImagesAutomatically = false
                    blockNetworkImage = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    userAgentString = BROWSER_UA
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, true)

                val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(CHALLENGE_TIMEOUT_SECONDS)

                fun poll() {
                    if (finished.get()) return
                    val cookies = allCookiesFor(targetUrl)
                    if (hasUsefulCookie(cookies) && System.currentTimeMillis() + WEBVIEW_SETTLE_MS < deadline) {
                        mainHandler.postDelayed({ finish(cookies) }, WEBVIEW_SETTLE_MS)
                        return
                    }
                    if (System.currentTimeMillis() >= deadline) {
                        finish(cookies.takeIf { hasUsefulCookie(it) })
                        return
                    }
                    mainHandler.postDelayed({ poll() }, 700L)
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val url = request?.url?.toString().orEmpty()
                        if (url.contains("google-analytics", true) || url.contains("googletagmanager", true)) {
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (finished.get()) return
                        view?.evaluateJavascript(
                            "(function(){return document.body ? document.body.innerText.slice(0,1200) : '';})()"
                        ) { bodyText ->
                            val cookies = allCookiesFor(url ?: targetUrl)
                            if (hasUsefulCookie(cookies) && !isChallengeBody(bodyText.orEmpty())) {
                                finish(cookies)
                            } else {
                                poll()
                            }
                        }
                    }

                    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                        if (!finished.get()) poll()
                    }
                }

                webView.loadUrl(BASE_URL, browserHeaders())
                mainHandler.postDelayed({ poll() }, 1200L)
            } catch (_: Exception) {
                finish(null)
            }
        }

        latch.await(CHALLENGE_TIMEOUT_SECONDS + 3L, TimeUnit.SECONDS)
        tryDestroyWebView(webViewRef.get())
        return result.get()
    }

    private fun browserHeaders(): Map<String, String> = mapOf(
        "User-Agent" to BROWSER_UA,
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache"
    )

    private fun allCookiesFor(request: Request): String = allCookiesFor(request.url.toString())

    private fun allCookiesFor(url: String): String {
        val cookieManager = CookieManager.getInstance()
        return mergeCookies(cookieManager.getCookie(BASE_URL), cookieManager.getCookie(url))
    }

    private fun hasUsefulCookie(cookies: String?): Boolean {
        if (cookies.isNullOrBlank()) return false
        val lower = cookies.lowercase(Locale.US)
        return lower.contains("imunify") ||
                lower.contains("revisit") ||
                lower.contains("shield") ||
                lower.contains("__ddg") ||
                lower.contains("captcha")
    }

    private fun mergeCookies(vararg cookieStrings: String?): String {
        val ordered = linkedMapOf<String, String>()
        cookieStrings.filterNotNull().flatMap { it.split(';') }.forEach { raw ->
            val trimmed = raw.trim()
            if (trimmed.isBlank() || !trimmed.contains('=')) return@forEach
            val name = trimmed.substringBefore('=').trim()
            val value = trimmed.substringAfter('=').trim()
            if (name.isNotBlank() && value.isNotBlank()) ordered[name] = value
        }
        return ordered.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun wafException(responseCode: Int): BackgroundAgentApiException = BackgroundAgentApiException(
        statusCode = responseCode,
        errorCode = "waf_blocked",
        message = WAF_ERROR_MESSAGE
    )

    private fun tryDestroyWebView(webViewRef: WebView?) {
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    webViewRef?.stopLoading()
                    webViewRef?.clearHistory()
                    webViewRef?.destroy()
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }
}
