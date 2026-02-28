package org.tabooproject.baikiruto.impl.item

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemSerializer
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemStreamData
import org.tabooproject.baikiruto.core.item.SerializedItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale

object DefaultItemSerializer : ItemSerializer {

    override fun serialize(itemStack: ItemStack): SerializedItem {
        val stream = Baikiruto.api().readItem(itemStack)
        return if (stream == null) {
            DefaultSerializedItem(
                itemId = "minecraft:${itemStack.type.name.lowercase(Locale.ENGLISH)}",
                amount = itemStack.amount,
                versionHash = "vanilla",
                metaHistory = emptyList(),
                runtimeData = emptyMap(),
                itemStackData = encodeItemStack(itemStack)
            )
        } else {
            serialize(stream)
        }
    }

    override fun serialize(itemStream: ItemStream): SerializedItem {
        return DefaultSerializedItem(
            itemId = itemStream.itemId,
            amount = itemStream.itemStack().amount,
            versionHash = itemStream.versionHash,
            metaHistory = itemStream.metaHistory,
            runtimeData = itemStream.runtimeData,
            itemStackData = encodeItemStack(itemStream.snapshot())
        )
    }

    override fun deserialize(json: String): ItemStream {
        val parsed = JsonParser.parseString(json)
        require(parsed is JsonObject) { "Serialized item json must be an object." }
        return deserialize(parsed)
    }

    override fun deserialize(json: JsonObject): ItemStream {
        return deserialize(fromLegacyJson(json))
    }

    override fun deserialize(serializedItem: SerializedItem): ItemStream {
        val itemStack = decodeItemStack(serializedItem.itemStackData).apply {
            amount = serializedItem.amount.coerceAtLeast(1)
        }
        val payload = ItemStreamData(
            itemId = serializedItem.itemId,
            versionHash = serializedItem.versionHash,
            metaHistory = serializedItem.metaHistory,
            runtimeData = serializedItem.runtimeData
        )
        return ItemStreamTransport.create(itemStack, payload)
    }

    private fun fromLegacyJson(json: JsonObject): SerializedItem {
        val itemId = readString(
            findElement(json, "id", "itemId", "item_id")
        )
            ?.takeIf { it.isNotEmpty() }
            ?: "minecraft:stone"
        val amount = readInt(
            findElement(json, "amount", "count"),
            defaultValue = 1
        )
            .coerceAtLeast(1)
        val runtimeData = linkedMapOf<String, Any?>()
        val dataElement = findElement(json, "data", "runtimeData", "runtime_data")
        when {
            dataElement == null || dataElement.isJsonNull -> Unit
            dataElement.isJsonObject -> runtimeData.putAll(jsonObjectToMapCompat(dataElement.asJsonObject))
            dataElement.isJsonPrimitive -> {
                val raw = readString(dataElement).orEmpty()
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    runCatching { JsonParser.parseString(raw) }
                        .getOrNull()
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.let { parsed -> runtimeData.putAll(jsonObjectToMapCompat(parsed)) }
                }
            }
        }
        val unique = findElement(json, "unique", "uniqueData", "unique_data")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
        if (unique != null) {
            readString(findElement(unique, "player", "name", "owner", "who"))
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    runtimeData["unique.player"] = it
                }
            readLong(findElement(unique, "date", "time", "timestamp"))
                ?.let {
                    runtimeData["unique.date"] = it
                }
            readString(findElement(unique, "uuid", "id"))
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    runtimeData["unique.uuid"] = it
                }
        }
        val uniqueEnabled = readBoolean(findElement(json, "unique-enabled", "uniqueEnabled"))
            ?: unique?.let { readBoolean(findElement(it, "enabled")) }
        if (uniqueEnabled == true || runtimeData.containsKey("unique.uuid")) {
            runtimeData["unique-enabled"] = true
        }
        val internal = findElement(json, "_baikiruto")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
        val versionHash = readString(
            findElement(internal, "version_hash", "versionHash", "version-hash")
        )
            ?.takeIf { it.isNotBlank() }
            ?: readString(findElement(json, "versionHash", "version_hash", "version-hash"))?.takeIf { it.isNotBlank() }
            ?: "legacy-json"
        val metaHistory = readStringList(
            findElement(internal, "meta_history", "metaHistory", "meta-history")
                ?: findElement(json, "metaHistory", "meta_history", "meta-history")
        )
            .ifEmpty {
                readString(
                    findElement(internal, "meta_history", "metaHistory", "meta-history")
                        ?: findElement(json, "metaHistory", "meta_history", "meta-history")
                )?.split(',', ';', '\n')
                    ?.mapNotNull { entry ->
                        entry.trim().takeIf { it.isNotEmpty() }
                    }
                    ?: emptyList()
            }
        val itemStackData = readString(
            findElement(internal, "item_stack_data", "itemStackData", "item-stack-data")
                ?: findElement(json, "itemStackData", "item_stack_data", "item-stack-data")
        )
            ?.takeIf { it.isNotBlank() }
            ?: buildFallbackItemStackData(itemId, amount)
        return DefaultSerializedItem(
            itemId = itemId,
            amount = amount,
            versionHash = versionHash,
            metaHistory = metaHistory,
            runtimeData = runtimeData,
            itemStackData = itemStackData
        )
    }

    private fun findElement(source: JsonObject?, vararg keys: String): JsonElement? {
        if (source == null) {
            return null
        }
        keys.forEach { key ->
            if (source.has(key)) {
                return source[key]
            }
        }
        return null
    }

    private fun readString(source: JsonElement?): String? {
        if (source == null || source.isJsonNull || !source.isJsonPrimitive) {
            return null
        }
        return runCatching { source.asString.trim() }
            .getOrNull()
    }

    private fun readInt(source: JsonElement?, defaultValue: Int): Int {
        if (source == null || source.isJsonNull || !source.isJsonPrimitive) {
            return defaultValue
        }
        val raw = runCatching { source.asString.trim() }.getOrNull() ?: return defaultValue
        return raw.toIntOrNull() ?: defaultValue
    }

    private fun readLong(source: JsonElement?): Long? {
        if (source == null || source.isJsonNull || !source.isJsonPrimitive) {
            return null
        }
        return runCatching { source.asString.trim() }
            .getOrNull()
            ?.toLongOrNull()
    }

    private fun readBoolean(source: JsonElement?): Boolean? {
        if (source == null || source.isJsonNull || !source.isJsonPrimitive) {
            return null
        }
        val raw = runCatching { source.asString.trim() }.getOrNull()?.lowercase(Locale.ENGLISH) ?: return null
        return when (raw) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    private fun readStringList(source: JsonElement?): List<String> {
        if (source == null || source.isJsonNull) {
            return emptyList()
        }
        if (source.isJsonArray) {
            return source.asJsonArray.mapNotNull { element ->
                if (!element.isJsonPrimitive) {
                    return@mapNotNull null
                }
                runCatching { element.asString.trim() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }
        if (source.isJsonPrimitive) {
            return readString(source)
                ?.split(',', ';', '\n')
                ?.mapNotNull { entry -> entry.trim().takeIf { it.isNotEmpty() } }
                ?: emptyList()
        }
        return emptyList()
    }

    private fun buildFallbackItemStackData(itemId: String, amount: Int): String {
        return runCatching {
            val itemStack = if (itemId.startsWith("minecraft:", ignoreCase = true)) {
                val materialName = itemId.substringAfter(':')
                    .uppercase(Locale.ENGLISH)
                ItemStack(Material.matchMaterial(materialName) ?: Material.STONE)
            } else {
                Baikiruto.api().buildItem(itemId) ?: ItemStack(Material.STONE)
            }
            itemStack.amount = amount.coerceAtLeast(1)
            encodeItemStack(itemStack)
        }.getOrElse {
            "AA=="
        }
    }

    private fun jsonObjectToMapCompat(source: JsonObject): Map<String, Any?> {
        val values = linkedMapOf<String, Any?>()
        source.entrySet().forEach { (key, value) ->
            values[key] = jsonElementToAnyCompat(value)
        }
        return values
    }

    private fun jsonElementToAnyCompat(source: JsonElement): Any? {
        if (source.isJsonNull) {
            return null
        }
        if (source.isJsonObject) {
            return jsonObjectToMapCompat(source.asJsonObject)
        }
        if (source.isJsonArray) {
            return source.asJsonArray.map { element ->
                if (element.isJsonNull) {
                    ""
                } else if (element.isJsonPrimitive) {
                    element.asJsonPrimitive.asString
                } else if (element.isJsonObject) {
                    jsonObjectToMapCompat(element.asJsonObject)
                } else if (element.isJsonArray) {
                    element.asJsonArray.map { child ->
                        if (child.isJsonPrimitive) child.asJsonPrimitive.asString else child.toString()
                    }
                } else {
                    element.toString()
                }
            }
        }
        return source.asJsonPrimitive.asString
    }

    private fun encodeItemStack(itemStack: ItemStack): String {
        val output = ByteArrayOutputStream()
        BukkitObjectOutputStream(output).use { stream ->
            stream.writeObject(itemStack)
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun decodeItemStack(raw: String): ItemStack {
        val bytes = runCatching { Base64.getDecoder().decode(raw) }.getOrNull() ?: return ItemStack(Material.STONE)
        val input = ByteArrayInputStream(bytes)
        return runCatching {
            BukkitObjectInputStream(input).use { stream ->
                (stream.readObject() as? ItemStack) ?: ItemStack(Material.STONE)
            }
        }.getOrDefault(ItemStack(Material.STONE))
    }
}
