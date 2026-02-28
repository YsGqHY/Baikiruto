package org.tabooproject.baikiruto.core.item

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

interface JsonContainer {

    fun toMap(): Map<String, Any>

    fun toJson(): String {
        return toJsonObject().toString()
    }

    fun toJsonObject(): JsonObject {
        val root = JsonObject()
        toMap().forEach { (key, value) ->
            root.add(key, value.toJsonElement())
        }
        return root
    }

    private fun Any?.toJsonElement(): JsonElement {
        return when (this) {
            null -> JsonNull.INSTANCE
            is JsonElement -> this
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            is Char -> JsonPrimitive(this)
            is Map<*, *> -> {
                val json = JsonObject()
                entries.forEach { (k, v) ->
                    val normalizedKey = k?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    json.add(normalizedKey, v.toJsonElement())
                }
                json
            }
            is Iterable<*> -> {
                com.google.gson.JsonArray().also { array ->
                    forEach { array.add(it.toJsonElement()) }
                }
            }
            is Array<*> -> {
                com.google.gson.JsonArray().also { array ->
                    forEach { array.add(it.toJsonElement()) }
                }
            }
            else -> JsonPrimitive(toString())
        }
    }
}
