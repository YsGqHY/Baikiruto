package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.inventory.ItemStack
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.getItemTag

object ItemNativeFeature {

    fun apply(itemStack: ItemStack, runtimeData: Map<String, Any?>) {
        val native = runtimeData["native"] as? Map<*, *> ?: return
        if (native.isEmpty()) {
            return
        }
        val fullTag = runCatching { itemStack.getItemTag() }.getOrDefault(ItemTag())
        native.forEach { (rawKey, rawValue) ->
            val path = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val encoded = encode(rawValue) ?: return@forEach
            putDeep(fullTag, path, encoded)
        }
        fullTag.saveTo(itemStack)
    }

    private fun encode(value: Any?): ItemTagData? {
        return when (value) {
            null -> null
            is ItemTagData -> value
            is Boolean -> ItemTagData(if (value) 1.toByte() else 0.toByte())
            is Byte -> ItemTagData(value)
            is Short -> ItemTagData(value)
            is Int -> ItemTagData(value)
            is Long -> ItemTagData(value)
            is Float -> ItemTagData(value)
            is Double -> ItemTagData(value)
            is Number -> ItemTagData(value.toDouble())
            is String -> ItemTagData(value)
            is ByteArray -> ItemTagData(value)
            is IntArray -> ItemTagData(value)
            is LongArray -> ItemTagData(value)
            is Map<*, *> -> {
                val compound = ItemTag()
                value.forEach { (rawKey, rawValue) ->
                    val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    val encoded = encode(rawValue) ?: return@forEach
                    compound[key] = encoded
                }
                compound
            }
            is Iterable<*> -> {
                val list = ItemTagList()
                value.forEach { raw ->
                    encode(raw)?.let(list::add)
                }
                list
            }
            else -> ItemTagData(value.toString())
        }
    }

    private fun putDeep(root: ItemTag, path: String, value: ItemTagData) {
        val keys = path.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (keys.isEmpty()) {
            return
        }
        var cursor = root
        keys.dropLast(1).forEach { key ->
            val existing = cursor[key]?.asCompound()
            if (existing != null) {
                cursor = existing
                return@forEach
            }
            val next = ItemTag()
            cursor[key] = next
            cursor = next
        }
        cursor[keys.last()] = value
    }
}
