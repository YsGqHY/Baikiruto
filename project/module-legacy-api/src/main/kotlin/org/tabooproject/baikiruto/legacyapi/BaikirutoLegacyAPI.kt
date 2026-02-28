package org.tabooproject.baikiruto.legacyapi

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.Inventory
import org.bukkit.util.io.BukkitObjectOutputStream
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemGroup
import org.tabooproject.baikiruto.core.item.ItemModel
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.MetaFactory
import org.tabooproject.baikiruto.core.item.SerializedItem
import taboolib.common.platform.function.getDataFolder
import taboolib.library.configuration.ConfigurationSection
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale

@Deprecated("Use org.tabooproject.baikiruto.core.Baikiruto#api()")
object BaikirutoLegacyAPI {

    val loaded: ArrayList<File>
        get() = arrayListOf()

    fun getItem(id: String, context: Map<String, Any?> = emptyMap()): ItemStack? {
        return Baikiruto.api().buildItem(id, context)
    }

    fun getItem(id: String, player: Player?): ItemStream? {
        return Baikiruto.api().getItemManager().generateItem(
            id,
            linkedMapOf<String, Any?>(
                "player" to player,
                "sender" to player
            )
        )
    }

    fun getItemStack(id: String, context: Map<String, Any?> = emptyMap()): ItemStack? {
        return getItem(id, context)
    }

    fun getItemStack(id: String, player: Player?): ItemStack? {
        return getItem(id, player)?.toItemStack()
    }

    fun read(item: ItemStack): ItemStream? {
        return Baikiruto.api().readItem(item)
    }

    fun getName(item: ItemStack): String? {
        return Baikiruto.api().getItemId(item)
    }

    fun getItem(item: ItemStack): Item? {
        return Baikiruto.api().getItemHandler().getItem(item)
    }

    fun getData(item: ItemStack): Map<String, Any?>? {
        return Baikiruto.api().getItemData(item)
    }

    fun getUnique(item: ItemStack): Map<String, Any?>? {
        return Baikiruto.api().getItemUniqueData(item)
    }

    fun checkUpdate(player: Player?, inventory: Inventory): Int {
        return Baikiruto.api().getItemUpdater().checkUpdate(player, inventory)
    }

    fun checkUpdate(player: Player?, item: ItemStack): ItemStack {
        return Baikiruto.api().getItemUpdater().checkUpdate(player, item)
    }

    fun checkUpdateToStream(player: Player?, item: ItemStack): ItemStream? {
        val updated = Baikiruto.api().getItemUpdater().checkUpdate(player, item)
        return Baikiruto.api().readItem(updated)
    }

    fun reload() {
        Baikiruto.api().reload()
    }

    fun reloadItem() {
        reload()
    }

    fun reloadDisplay() {
        reload()
    }

    fun loadedIds(): Set<String> {
        return Baikiruto.api().getItemLoader().loadedIds()
    }

    val folderItem: File
        get() = File(getDataFolder(), "items")

    val folderDisplay: File
        get() = File(getDataFolder(), "display")

    val registeredItem: Map<String, Item>
        get() = Baikiruto.api().getItemRegistry().entries()

    val registeredModel: Map<String, ItemModel>
        get() = Baikiruto.api().getModelRegistry().entries()

    val registeredDisplay: Map<String, ItemDisplay>
        get() = Baikiruto.api().getDisplayRegistry().entries()

    val registeredGroup: Map<String, ItemGroup>
        get() = Baikiruto.api().getGroupRegistry().entries()

    val registeredMeta: Map<String, MetaFactory>
        get() = Baikiruto.api().getMetaFactoryRegistry().entries()

    fun loadItemFromFile(file: File) {
        Baikiruto.api().getItemLoader().loadItemFromFile(file).forEach { item ->
            Baikiruto.api().registerItem(item)
        }
    }

    fun loadModelFromFile(file: File) {
        Baikiruto.api().getItemLoader().loadModelFromFile(file).forEach { model ->
            Baikiruto.api().getItemManager().registerModel(model)
        }
    }

    fun loadDisplayFromFile(file: File, fromItemFile: Boolean = false) {
        Baikiruto.api().getItemLoader().loadDisplayFromFile(file, fromItemFile).forEach { display ->
            Baikiruto.api().getItemManager().registerDisplay(display)
        }
    }

    fun readMeta(root: ConfigurationSection): MutableList<Meta> {
        return Baikiruto.api().getItemLoader().loadMetaFromSection(root).toMutableList()
    }

    fun serialize(item: ItemStack): SerializedItem {
        return Baikiruto.api().getItemSerializer().serialize(item)
    }

    fun serialize(stream: ItemStream): SerializedItem {
        return Baikiruto.api().getItemSerializer().serialize(stream)
    }

    fun serializeToJson(item: ItemStack): JsonObject {
        return toLegacyJson(serialize(item))
    }

    fun serializeToJson(stream: ItemStream): JsonObject {
        return toLegacyJson(serialize(stream))
    }

    fun serializeToJson(serializedItem: SerializedItem): JsonObject {
        return toLegacyJson(serializedItem)
    }

    fun serializeToJsonString(serializedItem: SerializedItem): String {
        return serializeToJson(serializedItem).toString()
    }

    fun serializeJson(item: ItemStack): JsonObject {
        return serializeToJson(item)
    }

    fun serializeJson(stream: ItemStream): JsonObject {
        return serializeToJson(stream)
    }

    fun serializeToJsonString(item: ItemStack): String {
        return serializeToJson(item).toString()
    }

    fun serializeToJsonString(stream: ItemStream): String {
        return serializeToJson(stream).toString()
    }

    fun deserialize(serializedItem: SerializedItem): ItemStream {
        return Baikiruto.api().getItemSerializer().deserialize(serializedItem)
    }

    fun deserialize(json: String): ItemStream {
        return deserialize(JsonParser.parseString(json).asJsonObject)
    }

    fun deserialize(json: JsonObject): ItemStream {
        return deserialize(deserializeToSerializedItem(json))
    }

    fun deserializeJson(json: String): ItemStream {
        return deserialize(json)
    }

    fun deserializeJson(json: JsonObject): ItemStream {
        return deserialize(json)
    }

    fun deserializeToSerializedItem(json: String): SerializedItem {
        return deserializeToSerializedItem(JsonParser.parseString(json).asJsonObject)
    }

    fun deserializeToSerializedItem(json: JsonObject): SerializedItem {
        return fromLegacyJson(json)
    }

    private fun toLegacyJson(serializedItem: SerializedItem): JsonObject {
        val json = JsonObject()
        json.addProperty("id", serializedItem.itemId)
        json.addProperty("itemId", serializedItem.itemId)
        json.addProperty("amount", serializedItem.amount)
        json.addProperty("count", serializedItem.amount)
        val runtimeData = LinkedHashMap(serializedItem.runtimeData)
        val unique = extractUniqueData(runtimeData)
        if (runtimeData.isNotEmpty()) {
            val dataJson = anyToJsonElement(runtimeData)
            json.add("data", dataJson)
            json.add("runtimeData", dataJson.deepCopy())
        }
        if (unique != null) {
            val uniqueJson = JsonObject()
            unique.player?.let { uniqueJson.addProperty("player", it) }
            uniqueJson.addProperty("date", unique.date)
            uniqueJson.addProperty("uuid", unique.uuid)
            json.add("unique", uniqueJson)
            json.add("uniqueData", uniqueJson.deepCopy())
        }
        val internal = JsonObject()
        internal.addProperty("version_hash", serializedItem.versionHash)
        if (serializedItem.metaHistory.isNotEmpty()) {
            val meta = JsonArray()
            serializedItem.metaHistory.forEach(meta::add)
            internal.add("meta_history", meta)
        }
        if (serializedItem.itemStackData.isNotBlank()) {
            internal.addProperty("item_stack_data", serializedItem.itemStackData)
        }
        json.add("_baikiruto", internal)
        json.addProperty("versionHash", serializedItem.versionHash)
        if (serializedItem.metaHistory.isNotEmpty()) {
            val metaHistory = JsonArray()
            serializedItem.metaHistory.forEach(metaHistory::add)
            json.add("metaHistory", metaHistory)
        }
        if (serializedItem.itemStackData.isNotBlank()) {
            json.addProperty("itemStackData", serializedItem.itemStackData)
        }
        return json
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
        return object : SerializedItem {
            override val itemId: String = itemId
            override val amount: Int = amount
            override val versionHash: String = versionHash
            override val metaHistory: List<String> = metaHistory
            override val runtimeData: Map<String, Any?> = runtimeData
            override val itemStackData: String = itemStackData
        }
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
            // Minimal payload when Bukkit runtime classes are unavailable.
            "AA=="
        }
    }

    private fun encodeItemStack(itemStack: ItemStack): String {
        val output = ByteArrayOutputStream()
        BukkitObjectOutputStream(output).use { stream ->
            stream.writeObject(itemStack)
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun extractUniqueData(runtimeData: MutableMap<String, Any?>): LegacyUniqueData? {
        val uuid = runtimeData.remove("unique.uuid")?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val date = runtimeData.remove("unique.date").asLongValueOrNull() ?: System.currentTimeMillis()
        val player = runtimeData.remove("unique.player")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return LegacyUniqueData(player = player, date = date, uuid = uuid)
    }

    private fun Any?.asLongValueOrNull(): Long? {
        return when (this) {
            null -> null
            is Number -> toLong()
            is String -> trim().toLongOrNull()
            else -> null
        }
    }

    private fun anyToJsonElement(source: Any?): JsonElement {
        return when (source) {
            null -> JsonNull.INSTANCE
            is JsonElement -> source
            is Boolean -> JsonPrimitive(source)
            is Number -> JsonPrimitive(source)
            is String -> JsonPrimitive(source)
            is Char -> JsonPrimitive(source)
            is Map<*, *> -> {
                val json = JsonObject()
                source.forEach { (key, value) ->
                    val normalizedKey = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    json.add(normalizedKey, anyToJsonElement(value))
                }
                json
            }
            is Iterable<*> -> {
                val json = JsonArray()
                source.forEach { json.add(anyToJsonElement(it)) }
                json
            }
            is Array<*> -> {
                val json = JsonArray()
                source.forEach { json.add(anyToJsonElement(it)) }
                json
            }
            else -> JsonPrimitive(source.toString())
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

    private data class LegacyUniqueData(
        val player: String?,
        val date: Long,
        val uuid: String
    )
}
