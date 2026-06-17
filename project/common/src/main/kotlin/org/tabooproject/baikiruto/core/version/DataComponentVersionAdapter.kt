package org.tabooproject.baikiruto.core.version

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import taboolib.common.platform.function.warning
import java.util.Locale

/**
 * 1.20.5+ Data Component API adapter.
 */
open class DataComponentVersionAdapter : BaseItemMetaVersionAdapter() {

    private val componentWrapper: ThreadLocal<ComponentItemWrapper?> = ThreadLocal.withInitial { null }

    override fun applyDisplayName(itemStack: ItemStack, displayName: String?) {
        if (displayName.isNullOrBlank()) {
            return
        }
        super.applyDisplayName(itemStack, displayName)
    }

    override fun applyLore(itemStack: ItemStack, lore: List<String>) {
        if (lore.isEmpty()) {
            return
        }
        super.applyLore(itemStack, lore)
    }

    override fun applyVersionEffects(itemStack: ItemStack, runtimeData: Map<String, Any?>) {
        super.applyVersionEffects(itemStack, runtimeData)
        applyDataComponents(itemStack, runtimeData)
    }

    private fun applyDataComponents(itemStack: ItemStack, runtimeData: Map<String, Any?>) {
        val componentsData = runtimeData["components"] as? Map<*, *> ?: return
        val wrapper = getOrCreateWrapper(itemStack)

        var publicBukkitValues: Any? = null
        componentsData.forEach { (key, value) ->
            if (key == null || value == null) return@forEach
            val componentKey = canonicalComponentKey(key.toString()) ?: return@forEach
            if (shouldPreserveLegacyDisplay(runtimeData, componentKey)) {
                return@forEach
            }

            try {
                when (componentKey) {
                    "minecraft:custom_data" -> publicBukkitValues = applyCustomData(wrapper, value) ?: publicBukkitValues
                    "minecraft:unbreakable" -> applyUnbreakableComponent(wrapper, value)
                    "minecraft:glider" -> applyUnitToggleComponent(wrapper, componentKey, value)
                    "minecraft:hide_tooltip", "minecraft:hide_additional_tooltip" -> applyUnitToggleComponent(wrapper, componentKey, value)
                    "minecraft:damage_resistant" -> applyDamageResistantComponent(wrapper, value)
                    else -> applyNormalizedComponent(wrapper, componentKey, value)
                }
            } catch (ex: Exception) {
                // common 模块无 lang 依赖，使用固定格式日志（与 BaikirutoLog 同策略）
                warning("[Baikiruto][COMPONENT_APPLY_FAILED] $componentKey -> ${ex.message}")
            }
        }
        applyPublicBukkitValues(itemStack, publicBukkitValues)
    }

    private fun applyNormalizedComponent(wrapper: ComponentItemWrapper, componentKey: String, value: Any) {
        val candidates = componentValueCandidates(componentKey, value)
        if (candidates.isEmpty()) {
            return
        }
        var lastError: Exception? = null
        candidates.forEach { candidate ->
            try {
                wrapper.setComponent(componentKey, candidate)
                return
            } catch (ex: Exception) {
                lastError = ex
            }
        }
        if (lastError != null) {
            throw lastError as Exception
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shouldPreserveLegacyDisplay(runtimeData: Map<String, Any?>, componentKey: String): Boolean = false

    private fun applyCustomData(wrapper: ComponentItemWrapper, value: Any): Any? {
        val source = value as? Map<*, *> ?: return null
        val incoming = linkedMapOf<String, Any?>()
        source.forEach { (rawKey, rawValue) ->
            val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            incoming[key] = rawValue
        }
        if (incoming.isEmpty()) {
            return null
        }
        val publicBukkitValues = incoming.entries.firstOrNull { (key, _) -> isPublicBukkitValuesKey(key) }?.value
        val componentIncoming = incoming.filterKeys { key -> !isPublicBukkitValuesKey(key) }
        if (componentIncoming.isNotEmpty()) {
            // 与已有 custom_data 合并而非替换，避免覆盖 ItemStreamTransport.sync() 写入的 baikiruto 运行时数据
            val existing = wrapper.getCustomDataMap()
            if (existing != null && existing.isNotEmpty()) {
                val merged = linkedMapOf<String, Any?>()
                existing.forEach { (k, v) -> merged[k.toString()] = v }
                componentIncoming.forEach { (k, v) ->
                    // 不覆盖 baikiruto 命名空间（由 sync() 管理）
                    if (k != "baikiruto") {
                        merged[k] = v
                    }
                }
                wrapper.setComponent("minecraft:custom_data", merged)
            } else {
                wrapper.setComponent("minecraft:custom_data", componentIncoming)
            }
        }
        return publicBukkitValues
    }

    private fun isPublicBukkitValuesKey(key: String): Boolean {
        return key.equals("PublicBukkitValues", ignoreCase = true)
    }

    private fun applyPublicBukkitValues(itemStack: ItemStack, value: Any?) {
        val values = value as? Map<*, *> ?: return
        if (values.isEmpty()) {
            return
        }
        val itemMeta = itemStack.itemMeta ?: return
        val container = itemMeta.persistentDataContainer
        var changed = false
        values.forEach { (rawKey, rawValue) ->
            val key = persistentDataKey(rawKey?.toString()) ?: return@forEach
            val persistentValue = parsePersistentCustomDataValue(key, rawValue) ?: return@forEach
            applyPersistentCustomDataValue(container, key, persistentValue)
            changed = true
        }
        if (changed) {
            itemStack.itemMeta = itemMeta
        }
    }

    private fun applyUnbreakableComponent(wrapper: ComponentItemWrapper, value: Any) {
        when (value) {
            is Boolean -> {
                if (value) {
                    wrapper.setComponent("minecraft:unbreakable", emptyMap<String, Any>())
                } else {
                    wrapper.removeComponent("minecraft:unbreakable")
                }
            }
            else -> wrapper.setComponent("minecraft:unbreakable", value)
        }
    }

    private fun applyUnitToggleComponent(wrapper: ComponentItemWrapper, componentKey: String, value: Any) {
        val boolean = booleanValue(value)
        if (boolean != null) {
            if (boolean) {
                wrapper.setComponent(componentKey, emptyMap<String, Any>())
            } else {
                wrapper.removeComponent(componentKey)
            }
            return
        }
        wrapper.setComponent(componentKey, value)
    }

    private fun applyDamageResistantComponent(wrapper: ComponentItemWrapper, value: Any) {
        val enabled = booleanValue(value)
        if (enabled != null) {
            if (!enabled) {
                wrapper.removeComponent("minecraft:damage_resistant")
            }
            return
        }
        applyNormalizedComponent(wrapper, "minecraft:damage_resistant", value)
    }

    private fun componentValueCandidates(componentKey: String, value: Any): List<Any> {
        return when (componentKey) {
            "minecraft:custom_name",
            "minecraft:item_name" -> {
                val candidates = linkedSetOf<Any>()
                normalizeTextComponent(value)?.let(candidates::add)
                candidates += value
                candidates.toList()
            }
            "minecraft:lore" -> {
                val candidates = linkedSetOf<Any>()
                normalizeLoreComponents(value)?.let(candidates::add)
                candidates += value
                candidates.toList()
            }
            "minecraft:enchantments" -> {
                listOfNotNull(normalizeEnchantments(value))
            }
            "minecraft:attribute_modifiers" -> {
                listOfNotNull(normalizeAttributeModifiers(value))
            }
            "minecraft:can_break",
            "minecraft:can_place_on" -> {
                listOfNotNull(normalizeAdventurePredicate(value))
            }
            "minecraft:use_remainder" -> {
                listOfNotNull(normalizeUseRemainder(value))
            }
            "minecraft:damage_resistant" -> {
                normalizeDamageResistantCandidates(value)
            }
            "minecraft:potion_contents" -> {
                listOfNotNull(normalizePotionContents(value))
            }
            "minecraft:custom_model_data" -> {
                val candidates = linkedSetOf<Any>()
                normalizeCustomModelData(value)?.let(candidates::add)
                candidates += value
                candidates.toList()
            }
            else -> listOf(value)
        }
    }

    private fun normalizeTextComponent(source: Any): Any? {
        val text = source as? String ?: return null
        parseJsonComponent(text)?.let { return it }
        return legacyTextToJsonComponent(text)
    }

    private fun normalizeLoreComponents(source: Any): Any? {
        return when (source) {
            is String -> normalizeTextComponent(source)?.let { component ->
                JsonArray().also { array -> array.add(component as JsonElement) }
            }
            is Iterable<*> -> {
                val converted = JsonArray()
                source.forEach { entry ->
                    val line = entry as? String ?: return null
                    val component = normalizeTextComponent(line) as? JsonElement ?: return null
                    converted.add(component)
                }
                converted.takeIf { it.size() > 0 }
            }
            else -> null
        }
    }

    private fun parseJsonComponent(source: String): JsonElement? {
        val trimmed = source.trim()
        if (!looksLikeJson(trimmed)) {
            return null
        }
        return try {
            JsonParser.parseString(trimmed)
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun looksLikeJson(source: String): Boolean {
        val trimmed = source.trim()
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
    }

    private fun legacyTextToJsonComponent(source: String): JsonObject {
        val root = JsonObject().apply {
            addProperty("text", "")
            addProperty("italic", false)
        }
        val extra = JsonArray()
        var color: String? = null
        val decorations = linkedMapOf(
            "bold" to false,
            "italic" to false,
            "underlined" to false,
            "strikethrough" to false,
            "obfuscated" to false
        )
        val buffer = StringBuilder()
        var index = 0
        while (index < source.length) {
            val current = source[index]
            if ((current == '&' || current == '§') && index + 1 < source.length) {
                val code = source[index + 1].lowercaseChar()
                if (isLegacyStyleCode(code)) {
                    flushTextSegment(buffer, extra, color, decorations)
                    val nextColor = LEGACY_COLOR_NAMES[code]
                    if (nextColor != null) {
                        decorations.keys.forEach { key -> decorations[key] = false }
                        color = nextColor
                    } else {
                        applyLegacyDecorationCode(code, decorations)
                        if (code == 'r') {
                            color = null
                        }
                    }
                    index += 2
                    continue
                }
            }
            buffer.append(current)
            index++
        }
        flushTextSegment(buffer, extra, color, decorations)
        if (extra.size() == 1) {
            return extra[0].asJsonObject
        }
        if (extra.size() > 0) {
            root.add("extra", extra)
        }
        return root
    }

    private fun isLegacyStyleCode(code: Char): Boolean {
        return code in LEGACY_COLOR_NAMES || code in LEGACY_DECORATION_CODES
    }

    private fun applyLegacyDecorationCode(code: Char, decorations: MutableMap<String, Boolean>) {
        when (code) {
            'k' -> decorations["obfuscated"] = true
            'l' -> decorations["bold"] = true
            'm' -> decorations["strikethrough"] = true
            'n' -> decorations["underlined"] = true
            'o' -> decorations["italic"] = true
            'r' -> decorations.keys.forEach { key -> decorations[key] = false }
        }
    }

    private fun flushTextSegment(
        buffer: StringBuilder,
        extra: JsonArray,
        color: String?,
        decorations: Map<String, Boolean>
    ) {
        if (buffer.isEmpty()) {
            return
        }
        extra.add(JsonObject().apply {
            addProperty("text", buffer.toString())
            addProperty("italic", decorations["italic"] == true)
            color?.let { addProperty("color", it) }
            decorations.forEach { (key, enabled) ->
                if (key != "italic" && enabled) {
                    addProperty(key, true)
                }
            }
        })
        buffer.clear()
    }

    private fun normalizeCustomModelData(source: Any): Any? {
        val number = when (source) {
            is Number -> source.toDouble()
            is String -> source.trim().toDoubleOrNull()
            is Map<*, *> -> {
                val map = source
                numberValue(map["value"])?.toDouble()
                    ?: numberValue(map["int"])?.toDouble()
                    ?: numberValue(map["custom-model-data"])?.toDouble()
                    ?: numberValue(map["custom_model_data"])?.toDouble()
            }
            else -> null
        } ?: return null
        return linkedMapOf(
            "floats" to listOf(number)
        )
    }

    private fun normalizeEnchantments(source: Any): Any? {
        val root = source as? Map<*, *> ?: return null
        val levelsSource = (root["levels"] as? Map<*, *>) ?: root
        val levels = linkedMapOf<String, Int>()
        levelsSource.forEach { (rawKey, rawValue) ->
            val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val level = numberValue(rawValue)?.toInt()?.coerceAtLeast(0) ?: return@forEach
            if (level > 0) {
                levels[normalizeNamespacedId(key)] = level
            }
        }
        return levels.takeIf { it.isNotEmpty() }
    }

    private fun normalizeAttributeModifiers(source: Any): Any? {
        val modifiers: Iterable<*> = when (source) {
            is Iterable<*> -> source
            is Map<*, *> -> source["modifiers"] as? Iterable<*> ?: return null
            else -> return null
        }

        val normalized = mutableListOf<Map<String, Any>>()
        modifiers.forEachIndexed { index, rawModifier ->
            val map = rawModifier as? Map<*, *> ?: return@forEachIndexed
            val type = map["type"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: map["attribute"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@forEachIndexed
            val amount = numberValue(map["amount"])?.toDouble() ?: return@forEachIndexed

            val entry = linkedMapOf<String, Any>(
                "type" to normalizeNamespacedId(type),
                "id" to normalizeAttributeModifierId(map["id"], type, index),
                "amount" to amount,
                "operation" to normalizeAttributeModifierOperation(map["operation"])
            )

            val slot = normalizeAttributeModifierSlot(map["slot"])
            if (slot != "any") {
                entry["slot"] = slot
            }
            normalized += entry
        }
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun normalizeAdventurePredicate(source: Any): Any? {
        val blocks = extractAdventureBlocks(source).distinct()

        if (blocks.isEmpty()) {
            return null
        }
        return linkedMapOf(
            "blocks" to blocks
        )
    }

    private fun normalizeUseRemainder(source: Any): Any? {
        return when (source) {
            is String -> {
                val id = source.trim().takeIf { it.isNotEmpty() } ?: return null
                val vanillaId = normalizeVanillaItemIdOrNull(id) ?: return null
                linkedMapOf(
                    "id" to vanillaId,
                    "count" to 1
                )
            }
            is Map<*, *> -> {
                val id = source["id"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: source["item"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: source["type"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return null
                val vanillaId = normalizeVanillaItemIdOrNull(id) ?: return null
                val amount = numberValue(source["count"])
                    ?: numberValue(source["amount"])
                    ?: 1
                linkedMapOf(
                    "id" to vanillaId,
                    "count" to amount.toInt().coerceAtLeast(1)
                )
            }
            else -> null
        }
    }

    private fun normalizeDamageResistantCandidates(source: Any): List<Any> {
        val tags = extractDamageTags(source)
        return tags.map { tag -> linkedMapOf("types" to tag) }
    }

    private fun extractDamageTags(source: Any): List<String> {
        return when (source) {
            is String -> normalizeDamageTagCandidates(source)
            is Iterable<*> -> source.flatMap { entry ->
                entry?.toString()?.let(::normalizeDamageTagCandidates).orEmpty()
            }
            is Map<*, *> -> {
                val enabled = booleanValue(source["enabled"]) ?: true
                if (!enabled) {
                    return emptyList()
                }
                val types = source["types"] ?: source["damage_types"]
                when (types) {
                    is String -> normalizeDamageTagCandidates(types)
                    is Iterable<*> -> types.flatMap { entry ->
                        entry?.toString()?.let(::normalizeDamageTagCandidates).orEmpty()
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }.distinct()
    }

    /**
     * 归一化 potion_contents 组件：将 custom_color 从 hex 字符串转为整数。
     * Minecraft 1.21.11 的 PotionContents.CODEC 期望 custom_color 为 Int。
     */
    private fun normalizePotionContents(source: Any): Any? {
        val map = source as? Map<*, *> ?: return source
        val result = linkedMapOf<String, Any?>()
        map.forEach { (k, v) ->
            val key = k?.toString() ?: return@forEach
            result[key] = if (key == "custom_color") {
                when (v) {
                    is Number -> v.toInt()
                    is String -> v.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
                        .toIntOrNull(16) ?: v
                    else -> v
                }
            } else v
        }
        return result
    }

    private fun extractAdventureBlocks(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> source.split(',', '\n').mapNotNull { token ->
                token.trim().takeIf { it.isNotEmpty() }?.let(::normalizeMaterialKey)
            }
            is Iterable<*> -> source.flatMap { entry -> extractAdventureBlocks(entry) }
            is Map<*, *> -> {
                val blocks = extractAdventureBlocks(source["blocks"])
                if (blocks.isNotEmpty()) {
                    blocks
                } else {
                    extractAdventureBlocks(source["predicates"])
                }
            }
            else -> source.toString().trim().takeIf { it.isNotEmpty() }?.let(::normalizeMaterialKey)?.let(::listOf)
                ?: emptyList()
        }
    }

    private fun normalizeMaterialKey(source: String): String {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        if (trimmed.startsWith("#")) {
            val tag = trimmed.substring(1)
            return "#${normalizeMaterialOrItemId(tag)}"
        }
        return normalizeMaterialOrItemId(trimmed)
    }

    private fun normalizeMaterialOrItemId(source: String): String {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        return if (':' in trimmed) {
            trimmed.lowercase(Locale.ENGLISH)
        } else {
            "minecraft:${trimmed.lowercase(Locale.ENGLISH)}"
        }
    }

    private fun normalizeVanillaItemIdOrNull(source: String): String? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        if (':' in trimmed) {
            val normalized = trimmed.lowercase(Locale.ENGLISH)
            return if (normalized.startsWith("minecraft:")) normalized else null
        }
        return "minecraft:${trimmed.lowercase(Locale.ENGLISH)}"
    }

    private fun normalizeNamespacedId(source: String): String {
        val trimmed = source.trim().lowercase(Locale.ENGLISH)
        return if (':' in trimmed) trimmed else "minecraft:$trimmed"
    }

    private fun normalizeAttributeModifierId(rawId: Any?, attributeId: String, index: Int): String {
        val defaultId = "baikiruto:${attributeId.substringAfter(':')}_$index"
        val source = rawId?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: defaultId
        val normalized = source.lowercase(Locale.ENGLISH)
        val split = normalized.split(':', limit = 2)
        val namespace = (if (split.size == 2) split[0] else "baikiruto")
            .replace(Regex("[^a-z0-9_.-]"), "_")
            .ifBlank { "baikiruto" }
        val path = (if (split.size == 2) split[1] else split[0])
            .replace(Regex("[^a-z0-9_./-]"), "_")
            .ifBlank { "modifier_$index" }
        return "$namespace:$path"
    }

    private fun normalizeAttributeModifierOperation(source: Any?): String {
        return when (source?.toString()?.trim()?.lowercase(Locale.ENGLISH)?.replace('-', '_')) {
            "add_value", "add_number" -> "add_value"
            "add_multiplied_base", "add_scalar", "multiply_base" -> "add_multiplied_base"
            "add_multiplied_total", "multiply_scalar_1", "multiply_total" -> "add_multiplied_total"
            else -> "add_value"
        }
    }

    private fun normalizeAttributeModifierSlot(source: Any?): String {
        return when (source?.toString()?.trim()?.lowercase(Locale.ENGLISH)?.replace('-', '_')) {
            null, "", "any" -> "any"
            "mainhand", "main_hand", "hand" -> "mainhand"
            "offhand", "off_hand" -> "offhand"
            "head", "helmet" -> "head"
            "chest", "chestplate", "body" -> "chest"
            "legs", "leggings" -> "legs"
            "feet", "boots" -> "feet"
            else -> source.toString().trim().lowercase(Locale.ENGLISH)
        }
    }

    private fun normalizeDamageTagCandidates(source: String): List<String> {
        val trimmed = source.trim().lowercase(Locale.ENGLISH)
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        if (trimmed.startsWith("#")) {
            val id = trimmed.substring(1)
            return listOf("#${if (':' in id) id else "minecraft:$id"}")
        }
        val token = trimmed.substringAfter(':').replace('-', '_')
        if (token in setOf("contact", "cactus", "sweet_berry_bush", "sweet_berry_bushes", "berry_bush")) {
            return listOf(
                "#minecraft:is_contact",
                "#minecraft:contact",
                "minecraft:cactus",
                "minecraft:sweet_berry_bush"
            )
        }
        val normalized = when (token) {
            "projectile" -> "is_projectile"
            "fire" -> "is_fire"
            "explosion" -> "is_explosion"
            "fall" -> "is_fall"
            "void", "out_of_world" -> "is_out_of_world"
            "magic" -> "is_magic"
            "lightning" -> "is_lightning"
            "freeze", "freezing" -> "is_freezing"
            else -> if (token.startsWith("is_")) token else "is_$token"
        }
        return listOf("#minecraft:$normalized")
    }

    private fun numberValue(source: Any?): Number? {
        return when (source) {
            is Number -> source
            is String -> source.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun persistentDataKey(source: String?): NamespacedKey? {
        val normalized = source?.trim()?.lowercase(Locale.ENGLISH)?.takeIf { it.isNotEmpty() } ?: return null
        val split = normalized.split(':', limit = 2)
        if (split.size != 2 || split[0].isBlank() || split[1].isBlank()) {
            return null
        }
        return NamespacedKey(split[0], split[1])
    }

    private fun parsePersistentCustomDataValue(key: NamespacedKey, source: Any?): PersistentCustomDataValue? {
        return when (source) {
            null -> null
            is Boolean -> PersistentCustomDataValue.ByteValue(if (source) 1 else 0)
            is Byte -> PersistentCustomDataValue.ByteValue(source.toInt())
            is Short -> PersistentCustomDataValue.ShortValue(source.toInt())
            is Int -> if (shouldTreatIntegerAsByte(key, source)) {
                PersistentCustomDataValue.ByteValue(source)
            } else {
                PersistentCustomDataValue.IntValue(source)
            }
            is Long -> PersistentCustomDataValue.LongValue(source)
            is Float -> PersistentCustomDataValue.FloatValue(source)
            is Double -> PersistentCustomDataValue.DoubleValue(source)
            is Number -> PersistentCustomDataValue.IntValue(source.toInt())
            is String -> parsePersistentCustomDataString(key, source)
            is Map<*, *> -> parsePersistentCustomDataMap(source)
            else -> PersistentCustomDataValue.StringValue(source.toString())
        }
    }

    private fun shouldTreatIntegerAsByte(key: NamespacedKey, value: Int): Boolean {
        return value in 0..1 && key.namespace == "cmilib" && key.key == "cmirainbowarmor"
    }

    private fun parsePersistentCustomDataMap(source: Map<*, *>): PersistentCustomDataValue? {
        val type = source["type"]?.toString()?.trim()?.lowercase(Locale.ENGLISH)?.replace('-', '_')
            ?: return null
        val value = source["value"] ?: source["data"] ?: source["val"] ?: return null
        return when (type) {
            "byte" -> numberValue(value)?.toInt()?.let { PersistentCustomDataValue.ByteValue(it) }
            "short" -> numberValue(value)?.toInt()?.let { PersistentCustomDataValue.ShortValue(it) }
            "int", "integer" -> numberValue(value)?.toInt()?.let { PersistentCustomDataValue.IntValue(it) }
            "long" -> numberValue(value)?.toLong()?.let { PersistentCustomDataValue.LongValue(it) }
            "float" -> numberValue(value)?.toFloat()?.let { PersistentCustomDataValue.FloatValue(it) }
            "double" -> numberValue(value)?.toDouble()?.let { PersistentCustomDataValue.DoubleValue(it) }
            "string" -> value.toString().let { PersistentCustomDataValue.StringValue(it) }
            "boolean", "bool" -> booleanValue(value)?.let { PersistentCustomDataValue.ByteValue(if (it) 1 else 0) }
            else -> null
        }
    }

    private fun parsePersistentCustomDataString(key: NamespacedKey, source: String): PersistentCustomDataValue? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        booleanValue(trimmed)?.let { return PersistentCustomDataValue.ByteValue(if (it) 1 else 0) }
        val suffix = trimmed.last().lowercaseChar()
        val body = trimmed.dropLast(1).trim()
        return when (suffix) {
            'b' -> body.toIntOrNull()?.let { PersistentCustomDataValue.ByteValue(it) }
            's' -> body.toIntOrNull()?.let { PersistentCustomDataValue.ShortValue(it) }
            'l' -> body.toLongOrNull()?.let { PersistentCustomDataValue.LongValue(it) }
            'f' -> body.toFloatOrNull()?.let { PersistentCustomDataValue.FloatValue(it) }
            'd' -> body.toDoubleOrNull()?.let { PersistentCustomDataValue.DoubleValue(it) }
            else -> trimmed.toIntOrNull()?.let { value ->
                if (shouldTreatIntegerAsByte(key, value)) PersistentCustomDataValue.ByteValue(value) else PersistentCustomDataValue.IntValue(value)
            } ?: trimmed.toLongOrNull()?.let { PersistentCustomDataValue.LongValue(it) }
                ?: trimmed.toDoubleOrNull()?.let { PersistentCustomDataValue.DoubleValue(it) }
                ?: PersistentCustomDataValue.StringValue(trimmed)
        }
    }

    private fun applyPersistentCustomDataValue(
        container: PersistentDataContainer,
        key: NamespacedKey,
        value: PersistentCustomDataValue
    ) {
        when (value) {
            is PersistentCustomDataValue.ByteValue -> container.set(key, PersistentDataType.BYTE, value.value.toByte())
            is PersistentCustomDataValue.ShortValue -> container.set(key, PersistentDataType.SHORT, value.value.toShort())
            is PersistentCustomDataValue.IntValue -> container.set(key, PersistentDataType.INTEGER, value.value)
            is PersistentCustomDataValue.LongValue -> container.set(key, PersistentDataType.LONG, value.value)
            is PersistentCustomDataValue.FloatValue -> container.set(key, PersistentDataType.FLOAT, value.value)
            is PersistentCustomDataValue.DoubleValue -> container.set(key, PersistentDataType.DOUBLE, value.value)
            is PersistentCustomDataValue.StringValue -> container.set(key, PersistentDataType.STRING, value.value)
        }
    }

    private sealed class PersistentCustomDataValue {
        data class ByteValue(val value: Int) : PersistentCustomDataValue()
        data class ShortValue(val value: Int) : PersistentCustomDataValue()
        data class IntValue(val value: Int) : PersistentCustomDataValue()
        data class LongValue(val value: Long) : PersistentCustomDataValue()
        data class FloatValue(val value: Float) : PersistentCustomDataValue()
        data class DoubleValue(val value: Double) : PersistentCustomDataValue()
        data class StringValue(val value: String) : PersistentCustomDataValue()
    }

    private fun canonicalComponentKey(source: String): String? {
        val normalized = source.trim()
            .lowercase(Locale.ENGLISH)
            .replace('-', '_')
            .removePrefix("minecraft:")
            .takeIf { it.isNotEmpty() }
            ?: return null
        val canonical = when (normalized) {
            "name" -> "custom_name"
            "enchantment" -> "enchantments"
            else -> normalized
        }
        return "minecraft:$canonical"
    }

    private fun getOrCreateWrapper(itemStack: ItemStack): ComponentItemWrapper {
        var wrapper = componentWrapper.get()
        if (wrapper == null || wrapper.getItemStack() !== itemStack) {
            wrapper = ComponentItemWrapper(itemStack)
            componentWrapper.set(wrapper)
        }
        return wrapper
    }

    private companion object {

        val LEGACY_DECORATION_CODES = setOf('k', 'l', 'm', 'n', 'o', 'r')

        val LEGACY_COLOR_NAMES = mapOf(
            '0' to "black",
            '1' to "dark_blue",
            '2' to "dark_green",
            '3' to "dark_aqua",
            '4' to "dark_red",
            '5' to "dark_purple",
            '6' to "gold",
            '7' to "gray",
            '8' to "dark_gray",
            '9' to "blue",
            'a' to "green",
            'b' to "aqua",
            'c' to "red",
            'd' to "light_purple",
            'e' to "yellow",
            'f' to "white"
        )
    }
}
