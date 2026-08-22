package com.zeus.code.local

import com.zeus.code.data.BackgroundAgentApi
import com.zeus.code.data.BackgroundAgentApiException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One LLM round-trip result with every tool invocation the model asked for,
 * regardless of whether it used native function calling or the text protocol.
 */
data class LocalParsedReply(
    val content: String,
    val toolCalls: List<LocalToolCall>
) {
    val wantsTools: Boolean get() = toolCalls.isNotEmpty()
}

/** Raised for any provider failure; [message] is user-presentable. */
class LocalLlmException(override val message: String, val status: Int = 0) : Exception(message)

/**
 * Calls any model source available in Local Mode:
 *  - NEBians relay (uses the paired account exactly like the cloud agent)
 *  - OpenAI-compatible endpoints directly from the device
 *    (OpenCode Zen, custom providers such as OpenRouter/Ollama/Groq)
 *
 * Tool invocations are understood from both native `tool_calls` and the
 * text protocol (`<<<TOOL_CALL>>>{...}<<<END>>>`), so even models behind a
 * plain-text relay can drive the agent loop reliably.
 */
class LocalLlmClient(
    private val nebiansApi: BackgroundAgentApi?,
    private val nebiansToken: () -> String?
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .callTimeout(320, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    /**
     * Runs one completion against the chosen [LocalModelChoice].
     * `history` contains only user/assistant/tool turns (no system prompt).
     */
    suspend fun complete(
        choice: LocalModelChoice,
        system: String,
        history: List<LocalMessage>,
        tools: List<LocalToolDef>
    ): LocalParsedReply = when (choice.source) {
        LocalSource.NEBIANS -> completeNebians(choice, system, history)
        LocalSource.ZEN -> completeOpenAiCompat(
            baseUrl = LocalProviderStore.ZEN_BASE_URL,
            apiKey = zenKeyProvider(),
            model = choice.model,
            system = system,
            history = history,
            tools = tools,
            zenUserAgent = true,
            label = "OpenCode Zen"
        )
        LocalSource.CUSTOM -> {
            val config = customConfigProvider(choice.customId)
                ?: throw LocalLlmException("Custom provider is no longer configured on this device.")
            completeOpenAiCompat(
                baseUrl = config.baseUrl.trimEnd('/'),
                apiKey = customKeyProvider(choice.customId),
                model = choice.model,
                system = system,
                history = history,
                tools = tools,
                zenUserAgent = false,
                label = config.label
            )
        }
        else -> throw LocalLlmException("No model selected.")
    }

    /** Fetches live model ids from an OpenAI-compatible `/models` endpoint. */
    fun listModels(baseUrl: String, apiKey: String): List<String> {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/models")
            .get()
            .apply {
                if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
                if (baseUrl.contains("opencode.ai")) header("User-Agent", LocalProviderStore.ZEN_USER_AGENT)
            }
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw LocalLlmException("Model list failed (HTTP ${response.code}).", response.code)
            val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyList()
            val ids = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id").orEmpty()
                if (id.isNotBlank()) ids += id
            }
            return ids.distinct()
        }
    }

    // ------------------------------------------------------------------
    // Pluggable key/config resolvers (wired by the engine owner)
    // ------------------------------------------------------------------

    var zenKeyProvider: () -> String = { "" }
    var customConfigProvider: (String) -> LocalCustomProvider? = { null }
    var customKeyProvider: (String) -> String = { "" }

    // ------------------------------------------------------------------
    // NEBians relay backend
    // ------------------------------------------------------------------

    private suspend fun completeNebians(
        choice: LocalModelChoice,
        system: String,
        history: List<LocalMessage>
    ): LocalParsedReply {
        val api = nebiansApi ?: throw LocalLlmException("Connect Zeus to NEBians to use its models locally.")
        val token = nebiansToken() ?: throw LocalLlmException("Connect Zeus to NEBians to use its models locally.")

        // The relay speaks plain role/content pairs; the agent loop keeps the
        // whole transcript as text, so tool calls/results ride inside content.
        val messages = history.map { entry ->
            val content = when (entry.role) {
                LocalRole.TOOL -> "[${entry.toolName.ifBlank { "tool" }} result]\n${entry.content}"
                else -> entry.content
            }
            rolePair(entry.role, content)
        }

        val response = try {
            api.chat(
                token = token,
                provider = choice.nebSlug,
                model = choice.model,
                messages = messages.map { (role, content) -> role to content },
                providerId = choice.nebRowId,
                system = system
            )
        } catch (error: BackgroundAgentApiException) {
            throw LocalLlmException(error.message, error.statusCode)
        }
        if (!response.ok && response.reply.isBlank()) {
            throw LocalLlmException(response.error ?: "NEBians returned no reply.")
        }
        val reply = response.reply
        val calls = TextProtocol.parse(reply)
        val visible = TextProtocol.strip(reply)
        return LocalParsedReply(visible.trim(), calls.mapIndexed { index, call ->
            call.copy(id = call.id.ifBlank { "neb_$index" })
        })
    }

    private fun rolePair(role: String, content: String): Pair<String, String> =
        if (role == LocalRole.TOOL) "user" to content else role to content

    // ------------------------------------------------------------------
    // Direct OpenAI-compatible backend (Zen + custom providers)
    // ------------------------------------------------------------------

    private fun completeOpenAiCompat(
        baseUrl: String,
        apiKey: String,
        model: String,
        system: String,
        history: List<LocalMessage>,
        tools: List<LocalToolDef>,
        zenUserAgent: Boolean,
        label: String
    ): LocalParsedReply {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "$label base URL must start with http(s)://"
        }
        if (apiKey.isBlank()) throw LocalLlmException("Add your $label API key first.")

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", openAiMessages(system, history))
            if (tools.isNotEmpty()) put("tools", toolsJson(tools))
            put("temperature", 0.2)
            put("stream", false)
        }

        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .apply {
                if (zenUserAgent) header("User-Agent", LocalProviderStore.ZEN_USER_AGENT)
            }
            .build()

        val raw = try {
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw LocalLlmException(
                    friendlyError(label, response.code, bodyText),
                    response.code
                )
                bodyText
            }
        } catch (error: IOException) {
            throw LocalLlmException("$label connection failed: ${error.message ?: "network error"}")
        }

        val json = runCatching { JSONObject(raw) }.getOrElse {
            throw LocalLlmException("$label sent an unreadable response.")
        }
        val message = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: throw LocalLlmException("$label sent no message.")
        return parseMessage(message, label)
    }

    private fun openAiMessages(system: String, history: List<LocalMessage>): JSONArray {
        val array = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        history.forEach { entry ->
            when (entry.role) {
                LocalRole.USER -> array.put(
                    JSONObject().put("role", "user").put("content", entry.content)
                )
                LocalRole.ASSISTANT -> {
                    val message = JSONObject().put("role", "assistant").put("content", entry.content)
                    array.put(message)
                }
                LocalRole.TOOL -> array.put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", "[${entry.toolName.ifBlank { "tool" }} result]\n${entry.content}")
                )
            }
        }
        return array
    }

    private fun toolsJson(tools: List<LocalToolDef>): JSONArray {
        val array = JSONArray()
        tools.forEach { tool ->
            array.put(
                JSONObject()
                    .put("type", "function")
                    .put(
                        "function",
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("parameters", JSONObject(tool.parameters.toString()))
                    )
            )
        }
        return array
    }

    private fun parseMessage(message: JSONObject, label: String): LocalParsedReply {
        val content = (message.opt("content") as? String).orEmpty()
        val calls = mutableListOf<LocalToolCall>()

        val native = message.optJSONArray("tool_calls")
        if (native != null) {
            for (i in 0 until native.length()) {
                val call = native.optJSONObject(i) ?: continue
                val function = call.optJSONObject("function") ?: continue
                val name = function.optString("name")
                if (name.isBlank() || name == "null") continue
                calls += LocalToolCall(
                    id = call.optString("id").ifBlank { "native_$i" },
                    name = name,
                    argumentsJson = function.optString("arguments").ifBlank { "{}" }
                )
            }
        }

        val parsed = TextProtocol.parse(content)
        calls += parsed.filter { candidate -> candidate.name !in calls.map { it.name } }
        val visible = TextProtocol.strip(content).trim()

        if (visible.isBlank() && calls.isEmpty()) {
            throw LocalLlmException("$label returned empty content.")
        }
        return LocalParsedReply(visible, calls)
    }

    private fun friendlyError(label: String, code: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")?.take(300)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(300)
        val limited = code == 429 || detail.contains("rate limit", true) ||
            detail.contains("FreeUsageLimit", true) || detail.contains("quota", true)
        return when {
            limited -> "$label quota is exhausted right now. Pick another model or retry later. [$code]"
            code == 401 || code == 403 -> "$label rejected the API key. Check the saved key. [$code]"
            detail.isNotBlank() -> "$label error [$code]: $detail"
            else -> "$label request failed (HTTP $code)."
        }
    }
}

/**
 * Parser for the transport-independent tool protocol embedded in model text:
 *
 * <<<TOOL_CALL>>>
 * {"tool": "read_file", "args": {"path": "src/App.kt"}}
 * <<<END>>>
 *
 * Bare JSON objects using `{"tool"/"name": ..., "args"/"arguments": ...}` are
 * accepted as a fallback because some models drop the markers.
 */
object TextProtocol {
    const val OPEN = "<<<TOOL_CALL>>>"
    const val CLOSE = "<<<END>>>"

    private val blockRegex = Regex(
        pattern = """<<<TOOL_CALL>>>\s*(\{.*?\})\s*<<<END>>>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL)
    )

    /** Extracts every tool call found in [text]. */
    fun parse(text: String): List<LocalToolCall> {
        val calls = mutableListOf<LocalToolCall>()
        blockRegex.findAll(text).forEach { match ->
            parseObject(match.groupValues[1])?.let { calls += it }
        }
        if (calls.isEmpty()) {
            // Fallback: bare single-object JSON lines the model may emit.
            lineLoop@ for (rawLine in text.lineSequence()) {
                val line = rawLine.trim()
                if (!(line.startsWith("{\"tool\"") || line.startsWith("{ \"tool\"") ||
                        line.startsWith("{\"name\"") || line.startsWith("{ \"name\""))
                ) continue@lineLoop
                if (line.contains(CLOSE)) continue
                parseObject(line)?.let { calls += it }
            }
        }
        return calls
    }

    /** [text] without the tool-call blocks — safe for display/history. */
    fun strip(text: String): String =
        blockRegex.replace(text) { match ->
            // Keep a short marker so the transcript shows something happened.
            val name = parseObject(match.groupValues[1])?.name.orEmpty()
            if (name.isBlank()) "" else "[used $name]"
        }

    private fun parseObject(raw: String): LocalToolCall? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val name = json.optString("tool", json.optString("name"))
        if (name.isBlank() || name == "null") return null
        val args = json.optJSONObject("args") ?: json.optJSONObject("arguments")
        return LocalToolCall(
            id = json.optString("id").ifBlank { "" },
            name = name,
            argumentsJson = (args ?: JSONObject()).toString()
        )
    }
}
