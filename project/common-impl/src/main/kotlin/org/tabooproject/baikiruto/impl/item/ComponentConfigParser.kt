package org.tabooproject.baikiruto.impl.item

import java.util.Locale

object ComponentConfigParser {

    private const val MINECRAFT_NAMESPACE = "minecraft:"
    private val JSON_TEXT_PATTERN = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
    private val JSON_COLOR_PATTERN = Regex("\"color\"\\s*:\\s*\"([^\"]+)\"")
    private val JSON_STYLE_PATTERNS = listOf(
        Regex("\"obfuscated\"\\s*:\\s*true", RegexOption.IGNORE_CASE) to "&k",
        Regex("\"bold\"\\s*:\\s*true", RegexOption.IGNORE_CASE) to "&l",
        Regex("\"strikethrough\"\\s*:\\s*true", RegexOption.IGNORE_CASE) to "&m",
        Regex("\"underlined\"\\s*:\\s*true", RegexOption.IGNORE_CASE) to "&n",
        Regex("\"italic\"\\s*:\\s*true", RegexOption.IGNORE_CASE) to "&o"
    )

    fun normalizeComponentKey(rawKey: String): String {
        val normalized = rawKey.trim()
            .lowercase(Locale.ENGLISH)
            .replace('-', '_')
        return if (normalized.startsWith(MINECRAFT_NAMESPACE)) {
            normalized.removePrefix(MINECRAFT_NAMESPACE)
        } else {
            normalized
        }
    }

    fun parseText(source: Any?): String? {
        return when (source) {
            null -> null
            is String -> parseTextString(source)
            is Map<*, *> -> parseTextMap(source)
            else -> source.toString().trim().takeIf { it.isNotEmpty() }
        }
    }

    fun parseTextList(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> parseText(source)?.let(::listOf) ?: emptyList()
            is Map<*, *> -> parseTextList(source)
            is Iterable<*> -> source.mapNotNull(::parseText)
            else -> parseText(source)?.let(::listOf) ?: emptyList()
        }
    }

    private fun parseTextString(source: String): String? {
        val raw = source.trim().takeIf { it.isNotEmpty() } ?: return null
        if (!raw.startsWith("{") || !raw.endsWith("}")) {
            return raw
        }
        val text = JSON_TEXT_PATTERN.find(raw)?.groupValues?.getOrNull(1)?.let(::unescapeJsonText) ?: return raw
        val formatted = StringBuilder()
        JSON_COLOR_PATTERN.find(raw)?.groupValues?.getOrNull(1)?.let { color ->
            legacyColorCode(color)?.let(formatted::append)
        }
        JSON_STYLE_PATTERNS.forEach { (pattern, code) ->
            if (pattern.containsMatchIn(raw)) {
                formatted.append(code)
            }
        }
        formatted.append(text)
        return formatted.toString()
    }

    private fun parseTextMap(source: Map<*, *>): String? {
        val normalized = normalizeMap(source)
        val direct = stringValue(normalized["text"])
            ?: stringValue(normalized["item_name"])
            ?: stringValue(normalized["value"])
            ?: stringValue(normalized["legacy"])
            ?: return null
        val parsed = parseTextString(direct) ?: return null
        if (direct.trim().startsWith("{")) {
            return parsed
        }
        val prefix = StringBuilder()
        legacyColorCode(stringValue(normalized["color"]))?.let(prefix::append)
        if (asBoolean(normalized["obfuscated"]) == true) {
            prefix.append("&k")
        }
        if (asBoolean(normalized["bold"]) == true) {
            prefix.append("&l")
        }
        if (asBoolean(normalized["strikethrough"]) == true) {
            prefix.append("&m")
        }
        if (asBoolean(normalized["underlined"]) == true || asBoolean(normalized["underline"]) == true) {
            prefix.append("&n")
        }
        if (asBoolean(normalized["italic"]) == true) {
            prefix.append("&o")
        }
        return if (prefix.isEmpty()) parsed else "$prefix$parsed"
    }

    private fun parseTextList(source: Map<*, *>): List<String> {
        val normalized = normalizeMap(source)
        val listSource = normalized["lines"]
            ?: normalized["values"]
            ?: normalized["item_description"]
            ?: normalized["lore"]
            ?: normalized["value"]
        val list = when (listSource) {
            is Iterable<*> -> listSource.mapNotNull(::parseText)
            null -> emptyList()
            else -> parseText(listSource)?.let(::listOf) ?: emptyList()
        }
        if (list.isNotEmpty()) {
            return list
        }
        return parseTextMap(normalized)?.let(::listOf) ?: emptyList()
    }

    private fun legacyColorCode(raw: String?): String? {
        val color = raw?.trim()?.lowercase(Locale.ENGLISH)?.takeIf { it.isNotEmpty() } ?: return null
        return when (color) {
            "black" -> "&0"
            "dark_blue" -> "&1"
            "dark_green" -> "&2"
            "dark_aqua" -> "&3"
            "dark_red" -> "&4"
            "dark_purple", "purple" -> "&5"
            "gold", "orange" -> "&6"
            "gray", "grey" -> "&7"
            "dark_gray", "dark_grey" -> "&8"
            "blue" -> "&9"
            "green" -> "&a"
            "aqua", "cyan" -> "&b"
            "red" -> "&c"
            "light_purple", "pink", "magenta" -> "&d"
            "yellow" -> "&e"
            "white" -> "&f"
            else -> if (HEX_COLOR.matches(color)) {
                "&#${color.substring(1).uppercase(Locale.ENGLISH)}"
            } else {
                null
            }
        }
    }

    private fun unescapeJsonText(source: String): String {
        return source
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun stringValue(source: Any?): String? {
        return when (source) {
            null -> null
            is String -> source.trim().takeIf { it.isNotEmpty() }
            is Number, is Boolean -> source.toString()
            else -> null
        }
    }

    private fun normalizeMap(source: Map<*, *>): Map<String, Any?> {
        return source.entries.mapNotNull { (key, value) ->
            val normalized = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            normalized to value
        }.toMap(linkedMapOf())
    }

    private fun asBoolean(source: Any?): Boolean? {
        return when (source) {
            null -> null
            is Boolean -> source
            is Number -> source.toInt() != 0
            is String -> when (source.trim().lowercase(Locale.ENGLISH)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }

    private val HEX_COLOR = Regex("#[0-9a-f]{6}")
}
