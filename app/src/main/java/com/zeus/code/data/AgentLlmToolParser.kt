package com.zeus.code.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Universal Tool & Action Parser for Reverse-Engineered Web Chat AI LLMs
 * and Structured JSON/XML Tool Calling Providers.
 *
 * Reverse-engineered web chat proxies (e.g. ChatGPT, Claude, Qwen, DeepSeek, K2-Think, Gemini)
 * often produce reasoning inside `<think>...</think>`, markdown code blocks (```json ... ```),
 * XML `<tool name="...">...</tool>` markup, or plain text with embedded JSON objects.
 *
 * This parser extracts thoughts, normalizes parameters, and converts any tool call
 * format into a consistent [ParsedLlmAction].
 */
data class ParsedLlmAction(
    val actionName: String,
    val thought: String = "",
    val target: String = "",
    val text: String = "",
    val x: Float? = null,
    val y: Float? = null,
    val startX: Float? = null,
    val startY: Float? = null,
    val endX: Float? = null,
    val endY: Float? = null,
    val durationMs: Long? = null,
    val packageName: String = "",
    val rawParameters: Map<String, String> = emptyMap(),
    val rawJson: String = ""
)

object AgentLlmToolParser {

    private val THINK_TAG_REGEX = Regex("<think(?:ing)?\\b[\\s\\S]*?</think(?:ing)?>", RegexOption.IGNORE_CASE)
    private val THOUGHT_TAG_REGEX = Regex("<thought\\b[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE)
    private val UNCLOSED_THINK_REGEX = Regex("<think(?:ing)?\\b[\\s\\S]*?\\z", RegexOption.IGNORE_CASE)

    private val XML_TOOL_TAG_REGEX = Regex(
        """<tool\b[^>]*name=["']([^"']+)["'][^>]*>([\s\S]*?)</tool>""",
        RegexOption.IGNORE_CASE
    )
    private val XML_TOOL_SELF_CLOSING = Regex(
        """<tool\b[^>]*name=["']([^"']+)["'][^>]*/>""",
        RegexOption.IGNORE_CASE
    )
    private val XML_ACTION_TAG_REGEX = Regex(
        """<action\b([^>]*)>([\s\S]*?)</action>""",
        RegexOption.IGNORE_CASE
    )
    private val XML_ACTION_SELF_CLOSING = Regex(
        """<action\b([^>]*)/?>""",
        RegexOption.IGNORE_CASE
    )
    private val XML_PARAM_REGEX = Regex(
        """<param\b[^>]*name=["']([^"']+)["'][^>]*>([\s\S]*?)</param>""",
        RegexOption.IGNORE_CASE
    )
    private val ATTR_REGEX = Regex("""([A-Za-z0-9_]+)=["']([^"']*)["']""")

    /**
     * Extracts reasoning/thinking content and returns a Pair of (CleanedContent, ThinkingContent).
     */
    fun extractThinking(content: String): Pair<String, String> {
        val thoughts = mutableListOf<String>()

        // 1. Matches <think>...</think>
        THINK_TAG_REGEX.findAll(content).forEach { match ->
            val inner = match.value
                .replace(Regex("^<think(?:ing)?[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</think(?:ing)?>$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (inner.isNotBlank()) thoughts.add(inner)
        }

        // 2. Matches <thought>...</thought>
        THOUGHT_TAG_REGEX.findAll(content).forEach { match ->
            val inner = match.value
                .replace(Regex("^<thought[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</thought>$", RegexOption.IGNORE_CASE), "")
                .trim()
            if (inner.isNotBlank()) thoughts.add(inner)
        }

        // 3. Clean content
        var cleaned = content
            .replace(THINK_TAG_REGEX, "")
            .replace(THOUGHT_TAG_REGEX, "")
            .replace(UNCLOSED_THINK_REGEX, "")
            .trim()

        return Pair(cleaned, thoughts.joinToString("\n\n"))
    }

    /**
     * Parses the single primary action from model reply text.
     */
    fun parseAction(rawText: String, defaultAction: String = "wait"): ParsedLlmAction {
        val (cleaned, extractedThought) = extractThinking(rawText)
        val actions = parseAllActions(cleaned, extractedThought)
        return actions.firstOrNull() ?: ParsedLlmAction(
            actionName = defaultAction,
            thought = extractedThought,
            text = cleaned,
            rawParameters = mapOf("text" to cleaned)
        )
    }

    /**
     * Parses all actions found in the model reply text (handling JSON, XML, Markdown blocks).
     */
    fun parseAllActions(content: String, fallbackThought: String = ""): List<ParsedLlmAction> {
        val results = mutableListOf<ParsedLlmAction>()

        // 1. Try parsing XML <tool name="..."> or <action ...>
        val xmlActions = parseXmlToolCalls(content, fallbackThought)
        if (xmlActions.isNotEmpty()) {
            return xmlActions
        }

        // 2. Try parsing Markdown code blocks ```json ... ``` or ``` ... ```
        val codeBlockActions = parseCodeBlockActions(content, fallbackThought)
        if (codeBlockActions.isNotEmpty()) {
            return codeBlockActions
        }

        // 3. Try parsing plain embedded JSON object or array
        val jsonActions = parseEmbeddedJson(content, fallbackThought)
        if (jsonActions.isNotEmpty()) {
            return jsonActions
        }

        // 4. Try parsing line-based action heuristics (e.g. "Action: tap(540, 960)")
        val heuristicAction = parseActionHeuristics(content, fallbackThought)
        if (heuristicAction != null) {
            results.add(heuristicAction)
        }

        return results
    }

    private fun parseXmlToolCalls(content: String, defaultThought: String): List<ParsedLlmAction> {
        val list = mutableListOf<ParsedLlmAction>()

        // <tool name="..."> ... </tool>
        XML_TOOL_TAG_REGEX.findAll(content).forEach { match ->
            val toolName = match.groupValues[1].trim()
            val body = match.groupValues[2]
            val params = mutableMapOf<String, String>()

            XML_PARAM_REGEX.findAll(body).forEach { pMatch ->
                val pName = pMatch.groupValues[1].trim()
                val pVal = unescapeXml(pMatch.groupValues[2].trim())
                params[pName] = pVal
            }

            // Also check for attributes in the tag itself
            ATTR_REGEX.findAll(match.value.substringBefore('>')).forEach { attr ->
                val k = attr.groupValues[1].trim()
                val v = unescapeXml(attr.groupValues[2].trim())
                if (!k.equals("name", ignoreCase = true)) {
                    params[k] = v
                }
            }

            list.add(buildActionFromMap(toolName, params, defaultThought, match.value))
        }

        // Self closing <tool name="..." ... />
        if (list.isEmpty()) {
            XML_TOOL_SELF_CLOSING.findAll(content).forEach { match ->
                val toolName = match.groupValues[1].trim()
                val params = mutableMapOf<String, String>()
                ATTR_REGEX.findAll(match.value).forEach { attr ->
                    val k = attr.groupValues[1].trim()
                    val v = unescapeXml(attr.groupValues[2].trim())
                    if (!k.equals("name", ignoreCase = true)) {
                        params[k] = v
                    }
                }
                list.add(buildActionFromMap(toolName, params, defaultThought, match.value))
            }
        }

        // <action type="..." ... />
        if (list.isEmpty()) {
            XML_ACTION_SELF_CLOSING.findAll(content).forEach { match ->
                val attrsStr = match.groupValues[1]
                val params = mutableMapOf<String, String>()
                ATTR_REGEX.findAll(attrsStr).forEach { attr ->
                    val k = attr.groupValues[1].trim()
                    val v = unescapeXml(attr.groupValues[2].trim())
                    params[k] = v
                }
                val actionName = params["type"] ?: params["action"] ?: params["name"] ?: "tap"
                list.add(buildActionFromMap(actionName, params, defaultThought, match.value))
            }
        }

        return list
    }

    private fun parseCodeBlockActions(content: String, defaultThought: String): List<ParsedLlmAction> {
        val list = mutableListOf<ParsedLlmAction>()
        val fencedRegex = Regex("```(?:json|xml)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

        fencedRegex.findAll(content).forEach { match ->
            val block = match.groupValues[1].trim()
            val parsed = parseJsonString(block, defaultThought)
            list.addAll(parsed)
        }

        return list
    }

    private fun parseEmbeddedJson(content: String, defaultThought: String): List<ParsedLlmAction> {
        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            val jsonCandidate = content.substring(firstBrace, lastBrace + 1)
            val parsed = parseJsonString(jsonCandidate, defaultThought)
            if (parsed.isNotEmpty()) return parsed
        }

        val firstBracket = content.indexOf('[')
        val lastBracket = content.lastIndexOf(']')
        if (firstBracket != -1 && lastBracket > firstBracket) {
            val jsonArrayCandidate = content.substring(firstBracket, lastBracket + 1)
            val parsed = parseJsonString(jsonArrayCandidate, defaultThought)
            if (parsed.isNotEmpty()) return parsed
        }

        return emptyList()
    }

    private fun parseJsonString(jsonStr: String, defaultThought: String): List<ParsedLlmAction> {
        val list = mutableListOf<ParsedLlmAction>()
        val trimmed = jsonStr.trim()

        if (trimmed.startsWith("{")) {
            runCatching {
                val obj = JSONObject(trimmed)
                list.add(buildActionFromJsonObject(obj, defaultThought))
            }
        } else if (trimmed.startsWith("[")) {
            runCatching {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    list.add(buildActionFromJsonObject(item, defaultThought))
                }
            }
        }
        return list
    }

    private fun parseActionHeuristics(content: String, defaultThought: String): ParsedLlmAction? {
        val lines = content.lines()
        for (line in lines) {
            val trimmed = line.trim()
            // e.g. "Tap(540, 960)" or "click(120, 300)"
            val tapMatch = Regex("""(?i)(?:action:\s*)?(?:tap|click)\s*\(\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)\s*\)""").find(trimmed)
            if (tapMatch != null) {
                val x = tapMatch.groupValues[1].toFloatOrNull() ?: 540f
                val y = tapMatch.groupValues[2].toFloatOrNull() ?: 960f
                return ParsedLlmAction(
                    actionName = "tap",
                    thought = defaultThought,
                    x = x,
                    y = y,
                    rawParameters = mapOf("x" to x.toString(), "y" to y.toString()),
                    rawJson = trimmed
                )
            }

            // e.g. "Swipe(500, 1600, 500, 400)"
            val swipeMatch = Regex("""(?i)(?:action:\s*)?swipe\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""").find(trimmed)
            if (swipeMatch != null) {
                val sx = swipeMatch.groupValues[1].toFloatOrNull() ?: 500f
                val sy = swipeMatch.groupValues[2].toFloatOrNull() ?: 1500f
                val ex = swipeMatch.groupValues[3].toFloatOrNull() ?: 500f
                val ey = swipeMatch.groupValues[4].toFloatOrNull() ?: 400f
                return ParsedLlmAction(
                    actionName = "swipe",
                    thought = defaultThought,
                    startX = sx,
                    startY = sy,
                    endX = ex,
                    endY = ey,
                    rawParameters = mapOf("startX" to sx.toString(), "startY" to sy.toString(), "endX" to ex.toString(), "endY" to ey.toString()),
                    rawJson = trimmed
                )
            }

            // e.g. "Launch(com.android.settings)" or "Open(Chrome)"
            val launchMatch = Regex("""(?i)(?:action:\s*)?(?:launch|open)\s*\(\s*([^)]+)\s*\)""").find(trimmed)
            if (launchMatch != null) {
                val pkg = launchMatch.groupValues[1].trim().removeSurrounding("\"", "'")
                return ParsedLlmAction(
                    actionName = "launch_app",
                    thought = defaultThought,
                    packageName = pkg,
                    target = pkg,
                    rawParameters = mapOf("package" to pkg),
                    rawJson = trimmed
                )
            }

            // e.g. "Type("hello world")"
            val typeMatch = Regex("""(?i)(?:action:\s*)?(?:type|input)\s*\(\s*["']?([^"']*)["']?\s*\)""").find(trimmed)
            if (typeMatch != null) {
                val txt = typeMatch.groupValues[1].trim()
                return ParsedLlmAction(
                    actionName = "type",
                    thought = defaultThought,
                    text = txt,
                    rawParameters = mapOf("text" to txt),
                    rawJson = trimmed
                )
            }
        }
        return null
    }

    private fun buildActionFromJsonObject(obj: JSONObject, defaultThought: String): ParsedLlmAction {
        val params = mutableMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            params[k] = obj.optString(k, "")
        }

        val actionName = (
            obj.optString("action")
                .ifBlank { obj.optString("action_name") }
                .ifBlank { obj.optString("name") }
                .ifBlank { obj.optString("type") }
                .ifBlank { obj.optString("tool") }
                .ifBlank { obj.optString("command") }
        ).trim()

        return buildActionFromMap(actionName, params, defaultThought, obj.toString())
    }

    private fun buildActionFromMap(
        actionName: String,
        params: Map<String, String>,
        defaultThought: String,
        raw: String
    ): ParsedLlmAction {
        val normalizedAction = normalizeActionName(
            if (actionName.isNotBlank()) actionName
            else params["action"] ?: params["action_name"] ?: params["name"] ?: params["type"] ?: "wait"
        )

        val thought = (
            params["thought"]
                ?: params["reason"]
                ?: params["explanation"]
                ?: params["plan"]
                ?: params["description"]
                ?: defaultThought
        ).trim()

        val target = (
            params["target"]
                ?: params["selector"]
                ?: params["element_id"]
                ?: params["element"]
                ?: params["zeus_id"]
                ?: params["url"]
                ?: params["link"]
                ?: params["query"]
                ?: params["app"]
                ?: params["package"]
                ?: params["key"]
                ?: ""
        ).trim()

        val text = (
            params["text"]
                ?: params["content"]
                ?: params["value"]
                ?: params["input"]
                ?: params["query"]
                ?: params["message"]
                ?: ""
        ).trim()

        val packageName = (
            params["package"]
                ?: params["package_name"]
                ?: params["app"]
                ?: params["target"]
                ?: ""
        ).trim()

        val x = parseFloatParam(params, "x", "point_x", "posX", "click_x")
        val y = parseFloatParam(params, "y", "point_y", "posY", "click_y")

        val startX = parseFloatParam(params, "startX", "start_x", "fromX", "from_x")
        val startY = parseFloatParam(params, "startY", "start_y", "fromY", "from_y")
        val endX = parseFloatParam(params, "endX", "end_x", "toX", "to_x")
        val endY = parseFloatParam(params, "endY", "end_y", "toY", "to_y")

        val durationMs = parseDurationMs(params)

        return ParsedLlmAction(
            actionName = normalizedAction,
            thought = thought,
            target = target,
            text = text,
            x = x,
            y = y,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            durationMs = durationMs,
            packageName = packageName,
            rawParameters = params,
            rawJson = raw
        )
    }

    private fun normalizeActionName(name: String): String {
        val lower = name.trim().lowercase().replace('-', '_').replace(' ', '_')
        return when (lower) {
            "tap", "click", "touch", "press", "click_element", "tap_coordinates" -> "tap"
            "long_press", "longpress", "press_and_hold", "hold" -> "long_press"
            "swipe", "drag", "slide" -> "swipe"
            "scroll_down", "scrolldown", "down" -> "scroll_down"
            "scroll_up", "scrollup", "up" -> "scroll_up"
            "scroll_left", "scrollleft", "left" -> "scroll_left"
            "scroll_right", "scrollright", "right" -> "scroll_right"
            "scroll" -> "scroll"
            "type", "input", "set_text", "send_keys", "write", "enter_text" -> "type"
            "launch_app", "launch", "open_app", "open", "start_app" -> "launch_app"
            "home", "key_home", "press_home", "go_home" -> "key_home"
            "back", "key_back", "press_back", "go_back" -> "key_back"
            "recents", "key_recents", "overview", "app_switch" -> "key_recents"
            "notifications", "open_notifications", "show_notifications" -> "open_notifications"
            "quick_settings", "open_quick_settings" -> "open_quick_settings"
            "screenshot", "capture_screenshot", "take_screenshot" -> "take_screenshot"
            "wait", "sleep", "delay", "pause" -> "wait"
            "finish", "done", "complete", "stop", "success" -> "finish"
            "navigate", "goto", "open_url", "visit" -> "navigate"
            "select", "choose_option", "dropdown" -> "select"
            "extract", "extract_content", "read_page", "get_content" -> "extract"
            "eval_script", "evaluate_js", "javascript", "exec_js" -> "eval_script"
            "search", "google_search", "web_search" -> "search"
            else -> lower
        }
    }

    private fun parseFloatParam(params: Map<String, String>, vararg keys: String): Float? {
        for (key in keys) {
            val v = params[key]?.trim() ?: continue
            val num = v.toFloatOrNull()
            if (num != null) return num
        }
        return null
    }

    private fun parseDurationMs(params: Map<String, String>): Long? {
        val directMs = params["duration_ms"]?.toLongOrNull() ?: params["timeout_ms"]?.toLongOrNull()
        if (directMs != null) return directMs

        val secs = params["seconds"]?.toDoubleOrNull()
            ?: params["duration"]?.replace("s", "")?.replace("sec", "")?.trim()?.toDoubleOrNull()
            ?: params["wait_seconds"]?.toDoubleOrNull()

        if (secs != null) return (secs * 1000).toLong()
        return null
    }

    private fun unescapeXml(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }
}
