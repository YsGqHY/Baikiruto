package org.tabooproject.baikiruto.impl.item

import org.bukkit.inventory.ItemStack
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object LockedDisplaySignature {

    const val LOCKED_DISPLAY_FIELDS_KEY = "__locked_display_fields__"
    const val LOCKED_DISPLAY_VALUES_KEY = "__locked_display_values__"
    const val LOCKED_DISPLAY_SIGNATURE_KEY = "__locked_display_signature__"

    fun withLockedValues(runtimeData: Map<String, Any?>, template: ItemStack): Map<String, Any?> {
        val fields = parseLockedFields(runtimeData[LOCKED_DISPLAY_FIELDS_KEY])
        if (fields.isEmpty()) {
            return withSignature(runtimeData)
        }
        val lockedValues = linkedMapOf<String, Any?>()
        val itemMeta = template.itemMeta
        if ("icon" in fields) {
            lockedValues["icon"] = template.type.name
        }
        if ("name" in fields) {
            lockedValues["name"] = mapOf(
                "item_name" to (itemMeta?.displayName ?: "")
            )
        }
        if ("lore" in fields) {
            lockedValues["lore"] = mapOf(
                "item_description" to (itemMeta?.lore?.toList() ?: emptyList<String>())
            )
        }
        return withSignature(
            LinkedHashMap(runtimeData).apply {
                this[LOCKED_DISPLAY_VALUES_KEY] = lockedValues
            }
        )
    }

    fun withSignature(runtimeData: Map<String, Any?>): Map<String, Any?> {
        val signature = compute(runtimeData)
        val current = runtimeData[LOCKED_DISPLAY_SIGNATURE_KEY] as? String
        if (signature == null) {
            if (current == null && runtimeData[LOCKED_DISPLAY_VALUES_KEY] == null) {
                return runtimeData
            }
            return LinkedHashMap(runtimeData).apply {
                remove(LOCKED_DISPLAY_SIGNATURE_KEY)
                remove(LOCKED_DISPLAY_VALUES_KEY)
            }
        }
        if (current == signature) {
            return runtimeData
        }
        return LinkedHashMap(runtimeData).apply {
            this[LOCKED_DISPLAY_SIGNATURE_KEY] = signature
        }
    }

    fun read(runtimeData: Map<String, Any?>): String? {
        val direct = runtimeData[LOCKED_DISPLAY_SIGNATURE_KEY] as? String
        if (!direct.isNullOrBlank()) {
            return direct
        }
        return compute(runtimeData)
    }

    private fun compute(runtimeData: Map<String, Any?>): String? {
        val fields = parseLockedFields(runtimeData[LOCKED_DISPLAY_FIELDS_KEY])
        if (fields.isEmpty()) {
            return null
        }
        val lockedValues = runtimeData[LOCKED_DISPLAY_VALUES_KEY] as? Map<*, *>
        val payload = linkedMapOf<String, Any?>()
        fields.sorted().forEach { field ->
            payload[field] = lockedValues?.get(field) ?: runtimeData[field]
        }
        return sha1(canonicalize(payload))
    }

    private fun parseLockedFields(raw: Any?): Set<String> {
        return when (raw) {
            is String -> raw.split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            is Iterable<*> -> raw.mapNotNull { entry ->
                entry?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            }.toSet()
            else -> emptySet()
        }
    }

    private fun canonicalize(source: Any?): String {
        return when (source) {
            null -> "null"
            is String -> "\"${escape(source)}\""
            is Number, is Boolean -> source.toString()
            is Map<*, *> -> source.entries
                .mapNotNull { (rawKey, value) ->
                    val key = rawKey?.toString() ?: return@mapNotNull null
                    key to canonicalize(value)
                }
                .sortedBy { it.first }
                .joinToString(prefix = "{", postfix = "}") { (key, value) ->
                    "\"${escape(key)}\":$value"
                }
            is Iterable<*> -> source.joinToString(prefix = "[", postfix = "]") { value ->
                canonicalize(value)
            }
            is Array<*> -> source.joinToString(prefix = "[", postfix = "]") { value ->
                canonicalize(value)
            }
            else -> "\"${escape(source.toString())}\""
        }
    }

    private fun escape(source: String): String {
        return source
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun sha1(source: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}
