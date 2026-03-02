package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
import java.util.Locale

object ItemBuildDebugMessenger {

    private val booleanKeywords = setOf("true", "false", "yes", "no", "on", "off", "null", "~")
    private val numberPattern = Regex("[-+]?\\d+(\\.\\d+)?([eE][-+]?\\d+)?")

    fun send(player: Player, rootKey: String, payload: Map<String, Any?>) {
        format(rootKey, payload).forEach(player::sendMessage)
    }

    internal fun format(rootKey: String, payload: Any?): List<String> {
        val lines = arrayListOf<String>()
        val normalizedRoot = rootKey.trim().ifEmpty { "baikiruto_debug" }
        when {
            isMapLike(payload) -> {
                val map = toLinkedMap(payload)
                if (map.isEmpty()) {
                    lines += "$normalizedRoot: {}"
                } else {
                    lines += "$normalizedRoot:"
                    appendMap(lines, map, indent = 2)
                }
            }
            isListLike(payload) -> {
                val list = toList(payload)
                if (list.isEmpty()) {
                    lines += "$normalizedRoot: []"
                } else {
                    lines += "$normalizedRoot:"
                    appendList(lines, list, indent = 2)
                }
            }
            else -> {
                lines += "$normalizedRoot: ${inlineScalar(payload)}"
            }
        }
        return lines
    }

    private fun appendMap(lines: MutableList<String>, source: Map<String, Any?>, indent: Int) {
        val pad = " ".repeat(indent)
        source.forEach { (rawKey, value) ->
            val key = yamlKey(rawKey)
            when {
                value is String && value.contains('\n') -> {
                    lines += "$pad$key: |"
                    value.split('\n').forEach { line ->
                        lines += "${" ".repeat(indent + 2)}$line"
                    }
                }
                isMapLike(value) -> {
                    val map = toLinkedMap(value)
                    if (map.isEmpty()) {
                        lines += "$pad$key: {}"
                    } else {
                        lines += "$pad$key:"
                        appendMap(lines, map, indent + 2)
                    }
                }
                isListLike(value) -> {
                    val list = toList(value)
                    if (list.isEmpty()) {
                        lines += "$pad$key: []"
                    } else {
                        lines += "$pad$key:"
                        appendList(lines, list, indent + 2)
                    }
                }
                else -> {
                    lines += "$pad$key: ${inlineScalar(value)}"
                }
            }
        }
    }

    private fun appendList(lines: MutableList<String>, source: List<Any?>, indent: Int) {
        val pad = " ".repeat(indent)
        source.forEach { entry ->
            when {
                entry is String && entry.contains('\n') -> {
                    lines += "$pad- |"
                    entry.split('\n').forEach { line ->
                        lines += "${" ".repeat(indent + 2)}$line"
                    }
                }
                isMapLike(entry) -> {
                    val map = toLinkedMap(entry)
                    if (map.isEmpty()) {
                        lines += "$pad- {}"
                    } else {
                        lines += "$pad-"
                        appendMap(lines, map, indent + 2)
                    }
                }
                isListLike(entry) -> {
                    val list = toList(entry)
                    if (list.isEmpty()) {
                        lines += "$pad- []"
                    } else {
                        lines += "$pad-"
                        appendList(lines, list, indent + 2)
                    }
                }
                else -> {
                    lines += "$pad- ${inlineScalar(entry)}"
                }
            }
        }
    }

    private fun yamlKey(source: String): String {
        return if (source.matches(Regex("[A-Za-z0-9_.-]+"))) {
            source
        } else {
            "'${source.replace("'", "''")}'"
        }
    }

    private fun inlineScalar(value: Any?): String {
        return when (value) {
            null -> "null"
            is Number, is Boolean -> value.toString()
            else -> quoteIfNeeded(value.toString())
        }
    }

    private fun quoteIfNeeded(value: String): String {
        if (value.isEmpty()) {
            return "''"
        }
        val normalized = value.lowercase(Locale.ENGLISH)
        val needsQuote = value.first().isWhitespace() ||
            value.last().isWhitespace() ||
            value.startsWith("-") ||
            value.startsWith("?") ||
            value.startsWith(":") ||
            value.contains(": ") ||
            value.contains('#') ||
            value.contains('{') ||
            value.contains('}') ||
            value.contains('[') ||
            value.contains(']') ||
            value.contains(',') ||
            booleanKeywords.contains(normalized) ||
            numberPattern.matches(value)
        return if (needsQuote) {
            "'${value.replace("'", "''")}'"
        } else {
            value
        }
    }

    private fun isMapLike(value: Any?): Boolean {
        return value is Map<*, *>
    }

    private fun isListLike(value: Any?): Boolean {
        return value is Iterable<*> ||
            value is Array<*> ||
            value is IntArray ||
            value is LongArray ||
            value is DoubleArray ||
            value is FloatArray ||
            value is ShortArray ||
            value is ByteArray ||
            value is CharArray ||
            value is BooleanArray
    }

    private fun toLinkedMap(value: Any?): Map<String, Any?> {
        val source = value as? Map<*, *> ?: return emptyMap()
        val result = linkedMapOf<String, Any?>()
        source.forEach { (rawKey, rawValue) ->
            val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            result[key] = rawValue
        }
        return result
    }

    private fun toList(value: Any?): List<Any?> {
        return when (value) {
            is Iterable<*> -> value.toList()
            is Array<*> -> value.toList()
            is IntArray -> value.map { it }
            is LongArray -> value.map { it }
            is DoubleArray -> value.map { it }
            is FloatArray -> value.map { it }
            is ShortArray -> value.map { it }
            is ByteArray -> value.map { it }
            is CharArray -> value.map { it }
            is BooleanArray -> value.map { it }
            else -> emptyList()
        }
    }
}
