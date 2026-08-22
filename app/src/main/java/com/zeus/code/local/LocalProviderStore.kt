package com.zeus.code.local

import android.content.Context
import com.zeus.code.data.SecureTokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A user-configured OpenAI-compatible provider stored on the device. */
@Serializable
data class LocalCustomProvider(
    val id: String,
    val name: String = "",
    val baseUrl: String = "",
    val defaultModel: String = "",
    val models: List<String> = emptyList(),
    val contextWindow: Int = 128000,
    val enabled: Boolean = true
) {
    val label: String get() = name.ifBlank { "Custom provider" }
}

/**
 * Device-side registry of every model source usable by the Local Agent:
 *  - NEBians providers (relayed through the paired background-agent account)
 *  - OpenCode Zen (direct, key from opencode.ai/auth)
 *  - Custom OpenAI-compatible endpoints (OpenRouter, Ollama, Groq, proxies...)
 *
 * API keys never leave the device; they are sealed in Android Keystore through
 * [SecureTokenStore] namespaces.
 */
class LocalProviderStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("zeus_local_agent", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ------------------------------------------------------------------
    // OpenCode Zen
    // ------------------------------------------------------------------

    private val zenStore by lazy { SecureTokenStore(appContext, "local_zen") }

    fun setZenKey(key: String) {
        if (key.isBlank()) zenStore.clear() else zenStore.save(key.trim())
        prefs.edit().putString(ZEN_MASKED, maskKey(key)).apply()
    }

    fun zenKey(): String = zenStore.read().orEmpty()

    fun zenKeyMasked(): String = prefs.getString(ZEN_MASKED, "").orEmpty()

    fun zenConfigured(): Boolean = zenKey().isNotBlank()

    // ------------------------------------------------------------------
    // Custom providers
    // ------------------------------------------------------------------

    fun customProviders(): List<LocalCustomProvider> =
        prefs.getString(CUSTOM_PROVIDERS, null)?.let { raw ->
            runCatching { json.decodeFromString<List<LocalCustomProvider>>(raw) }.getOrNull()
        }.orEmpty().sortedBy { it.label.lowercase() }

    fun customProvider(id: String): LocalCustomProvider? =
        customProviders().firstOrNull { it.id == id }

    fun saveCustomProvider(provider: LocalCustomProvider) {
        persistCustom(customProviders().filterNot { it.id == provider.id } + provider)
    }

    fun deleteCustomProvider(id: String) {
        persistCustom(customProviders().filterNot { it.id == id })
        keyStore(id).clear()
        prefs.edit().remove(CUSTOM_KEY_PREFIX + id).apply()
    }

    fun setCustomKey(id: String, key: String) {
        val store = keyStore(id)
        if (key.isBlank()) store.clear() else store.save(key.trim())
        prefs.edit().putString(CUSTOM_KEY_PREFIX + id, maskKey(key)).apply()
    }

    fun customKey(id: String): String = keyStore(id).read().orEmpty()

    fun customKeyMasked(id: String): String = prefs.getString(CUSTOM_KEY_PREFIX + id, "").orEmpty()

    private fun keyStore(id: String): SecureTokenStore =
        SecureTokenStore(appContext, "local_custom_${id.replace(Regex("[^A-Za-z0-9_-]"), "_")}")

    private fun persistCustom(list: List<LocalCustomProvider>) {
        prefs.edit().putString(CUSTOM_PROVIDERS, json.encodeToString(list)).apply()
    }

    companion object {
        /** OpenCode Zen OpenAI-compatible endpoint. */
        const val ZEN_BASE_URL = "https://opencode.ai/zen/v1"
        const val ZEN_KEY_URL = "https://opencode.ai/auth"

        /** Free coding models commonly exposed by Zen; live list is fetched at runtime. */
        val ZEN_FALLBACK_MODELS = listOf(
            "code-supernova",
            "big-pickle",
            "grok-code",
            "x-preview-f-free",
            "mimo-v2",
            "qwen3-coder-next"
        )

        /** Official client marker — Zen gates free models to OpenCode clients via User-Agent. */
        const val ZEN_USER_AGENT = "opencode/1.18.16"

        private const val ZEN_MASKED = "zen_key_masked"
        private const val CUSTOM_PROVIDERS = "custom_providers"
        private const val CUSTOM_KEY_PREFIX = "custom_key_masked_"

        fun maskKey(key: String): String {
            val trimmed = key.trim()
            if (trimmed.length <= 8) return if (trimmed.isBlank()) "" else "••••"
            return "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
        }
    }
}
