package org.tabooproject.baikiruto.impl.item

import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.item.ItemStreamData
import taboolib.module.nms.ItemTag
import taboolib.module.nms.ItemTagData
import taboolib.module.nms.ItemTagList
import taboolib.module.nms.ItemTagType
import taboolib.module.nms.getItemTag

object ItemStreamTransport {

    private const val ROOT = "baikiruto"
    private const val ID = "id"
    private const val VERSION = "version"
    private const val META_HISTORY = "meta_history"
    private const val DATA = "data"

    fun read(itemStack: ItemStack): ItemStreamData? {
        val root = rootTag(itemStack) ?: return null
        val itemId = root[ID]?.asString()?.takeIf { it.isNotBlank() } ?: return null
        val versionHash = root[VERSION]?.asString()?.takeIf { it.isNotBlank() } ?: "unknown"
        val metaHistory = root[META_HISTORY]?.asList()?.map { it.asString() } ?: emptyList()
        val runtimeData = root[DATA]?.asCompound()?.let { decodeMap(it) } ?: emptyMap()
        return ItemStreamData(
            itemId = itemId,
            versionHash = versionHash,
            metaHistory = metaHistory,
            runtimeData = runtimeData
        )
    }

    fun create(itemStack: ItemStack, payload: ItemStreamData): DefaultItemStream {
        return DefaultItemStream(
            backingItem = itemStack.clone(),
            itemId = payload.itemId,
            versionHash = payload.versionHash,
            initialRuntimeData = payload.runtimeData,
            initialMetaHistory = payload.metaHistory
        )
    }

    fun sync(
        itemStack: ItemStack,
        itemId: String,
        versionHash: String,
        metaHistory: List<String>,
        runtimeData: Map<String, Any?>
    ) {
        val fullTag = runCatching { itemStack.getItemTag() }.getOrDefault(ItemTag())
        val root = ItemTag().also { compound ->
            compound[ID] = ItemTagData(itemId)
            compound[VERSION] = ItemTagData(versionHash)
            compound[META_HISTORY] = ItemTagList.of(*metaHistory.map { ItemTagData(it) }.toTypedArray())
            compound[DATA] = encodeMap(runtimeData)
        }
        fullTag[ROOT] = root
        fullTag.saveTo(itemStack)
    }

    private fun rootTag(itemStack: ItemStack): ItemTag? {
        val tag = runCatching { itemStack.getItemTag() }.getOrNull() ?: return null
        return tag[ROOT]?.asCompound()
    }

    private fun encodeMap(values: Map<String, Any?>): ItemTag {
        val encoded = ItemTag()
        values.forEach { (key, value) ->
            encodeData(value)?.let { encoded[key] = it }
        }
        return encoded
    }

    private fun encodeData(value: Any?): ItemTagData? {
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
                val map = ItemTag()
                value.forEach { (k, v) ->
                    val key = k?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                    encodeData(v)?.let { map[key] = it }
                }
                map
            }
            is Iterable<*> -> {
                val list = ItemTagList()
                value.forEach { element ->
                    encodeData(element)?.let(list::add)
                }
                list
            }
            is Array<*> -> {
                val list = ItemTagList()
                value.forEach { element ->
                    encodeData(element)?.let(list::add)
                }
                list
            }
            else -> null
        }
    }

    private fun decodeMap(tag: ItemTag): Map<String, Any?> {
        val decoded = linkedMapOf<String, Any?>()
        tag.forEach { (key, value) ->
            decoded[key] = decodeData(value)
        }
        return decoded
    }

    private fun decodeData(value: ItemTagData): Any? {
        return when (value.type) {
            ItemTagType.END -> null
            ItemTagType.BYTE -> {
                val byteValue = value.asByte()
                if (byteValue == 0.toByte() || byteValue == 1.toByte()) {
                    byteValue.toInt() == 1
                } else {
                    byteValue
                }
            }
            ItemTagType.SHORT -> value.asShort()
            ItemTagType.INT -> value.asInt()
            ItemTagType.LONG -> value.asLong()
            ItemTagType.FLOAT -> value.asFloat()
            ItemTagType.DOUBLE -> value.asDouble()
            ItemTagType.BYTE_ARRAY -> value.asByteArray()
            ItemTagType.STRING -> value.asString()
            ItemTagType.LIST -> value.asList().map { decodeData(it) }
            ItemTagType.COMPOUND -> decodeMap(value.asCompound())
            ItemTagType.INT_ARRAY -> value.asIntArray()
            ItemTagType.LONG_ARRAY -> value.asLongArray()
        }
    }
}
