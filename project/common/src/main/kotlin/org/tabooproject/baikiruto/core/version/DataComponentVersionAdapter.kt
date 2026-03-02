package org.tabooproject.baikiruto.core.version

import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import taboolib.library.reflex.LazyClass
import taboolib.library.reflex.Reflex.Companion.invokeMethod
import taboolib.library.reflex.ReflexClass
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

        if (trySetComponentDisplayName(itemStack, displayName)) {
            return
        }

        val itemMeta = itemStack.itemMeta ?: return
        val component = createLegacyTextComponent(displayName)
        if (component != null && runCatching { itemMeta.invokeMethod<Any?>("setItemName", component) }.isSuccess) {
            itemStack.itemMeta = itemMeta
            return
        }

        super.applyDisplayName(itemStack, displayName)
    }

    override fun applyLore(itemStack: ItemStack, lore: List<String>) {
        if (lore.isEmpty()) {
            return
        }

        if (trySetComponentLore(itemStack, lore)) {
            return
        }

        val itemMeta = itemStack.itemMeta ?: return
        val components = lore.mapNotNull { createLegacyTextComponent(it) }
        if (components.size == lore.size && runCatching { itemMeta.invokeMethod<Any?>("setLore", components) }.isSuccess) {
            itemStack.itemMeta = itemMeta
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

        componentsData.forEach { (key, value) ->
            if (key == null || value == null) return@forEach
            val componentKey = canonicalComponentKey(key.toString()) ?: return@forEach

            try {
                when (componentKey) {
                    "minecraft:custom_data" -> applyCustomData(wrapper, value)
                    "minecraft:unbreakable" -> applyUnbreakableComponent(wrapper, value)
                    "minecraft:glider" -> applyUnitToggleComponent(wrapper, componentKey, value)
                    "minecraft:damage_resistant" -> applyDamageResistantComponent(wrapper, value)
                    else -> applyNormalizedComponent(wrapper, componentKey, value)
                }
            } catch (ex: Exception) {
                warning("Failed to apply component $componentKey: ${ex.message}")
            }
        }
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

    private fun applyCustomData(wrapper: ComponentItemWrapper, value: Any) {
        val source = value as? Map<*, *> ?: return
        val incoming = linkedMapOf<String, Any?>()
        source.forEach { (rawKey, rawValue) ->
            val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            incoming[key] = rawValue
        }
        if (incoming.isEmpty()) {
            return
        }
        wrapper.setComponent("minecraft:custom_data", incoming)
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
        when (value) {
            is Boolean -> {
                if (value) {
                    wrapper.setComponent(componentKey, emptyMap<String, Any>())
                } else {
                    wrapper.removeComponent(componentKey)
                }
            }
            else -> wrapper.setComponent(componentKey, value)
        }
    }

    private fun applyDamageResistantComponent(wrapper: ComponentItemWrapper, value: Any) {
        if (value is Boolean && !value) {
            wrapper.removeComponent("minecraft:damage_resistant")
            return
        }
        applyNormalizedComponent(wrapper, "minecraft:damage_resistant", value)
    }

    private fun componentValueCandidates(componentKey: String, value: Any): List<Any> {
        return when (componentKey) {
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
                listOfNotNull(normalizeDamageResistant(value))
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

    private fun normalizeDamageResistant(source: Any): Any? {
        val normalizedTag = when (source) {
            is String -> normalizeDamageTag(source)
            is Iterable<*> -> source.firstNotNullOfOrNull { entry ->
                entry?.toString()?.let(::normalizeDamageTag)
            }
            is Map<*, *> -> {
                val enabled = booleanValue(source["enabled"]) ?: true
                if (!enabled) {
                    return null
                }
                val types = source["types"] ?: source["damage_types"]
                when (types) {
                    is String -> normalizeDamageTag(types)
                    is Iterable<*> -> types.firstNotNullOfOrNull { entry ->
                        entry?.toString()?.let(::normalizeDamageTag)
                    }
                    else -> null
                }
            }
            else -> null
        } ?: return null

        return linkedMapOf(
            "types" to normalizedTag
        )
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

    private fun normalizeDamageTag(source: String): String? {
        val trimmed = source.trim().lowercase(Locale.ENGLISH)
        if (trimmed.isEmpty()) {
            return null
        }
        if (trimmed.startsWith("#")) {
            val id = trimmed.substring(1)
            return "#${if (':' in id) id else "minecraft:$id"}"
        }
        val token = trimmed.substringAfter(':').replace('-', '_')
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
        return "#minecraft:$normalized"
    }

    private fun numberValue(source: Any?): Number? {
        return when (source) {
            is Number -> source
            is String -> source.trim().toDoubleOrNull()
            else -> null
        }
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

    private fun trySetComponentDisplayName(itemStack: ItemStack, displayName: String): Boolean {
        return runCatching {
            val wrapper = getOrCreateWrapper(itemStack)
            val component = createLegacyTextComponent(displayName) ?: return false
            wrapper.setJavaComponent("minecraft:item_name", component)
            true
        }.getOrElse { false }
    }

    private fun trySetComponentLore(itemStack: ItemStack, lore: List<String>): Boolean {
        return runCatching {
            val wrapper = getOrCreateWrapper(itemStack)
            val components = lore.mapNotNull { createLegacyTextComponent(it) }
            if (components.size != lore.size) return false
            wrapper.setJavaComponent("minecraft:lore", components)
            true
        }.getOrElse { false }
    }

    private fun getOrCreateWrapper(itemStack: ItemStack): ComponentItemWrapper {
        var wrapper = componentWrapper.get()
        if (wrapper == null || wrapper.getItemStack() !== itemStack) {
            wrapper = ComponentItemWrapper(itemStack)
            componentWrapper.set(wrapper)
        }
        return wrapper
    }

    private fun createLegacyTextComponent(text: String): Any? {
        val serializerClass = runCatching {
            LazyClass.of(
                source = "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer",
                dimensions = 0,
                isPrimitive = false,
                classFinder = null
            ).instance
        }.getOrNull() ?: return null
        val serializerReflex = runCatching { ReflexClass.of(serializerClass as Class<*>) }.getOrNull() ?: return null
        val legacyAmpersand = runCatching {
            serializerReflex.getMethodSilently("legacyAmpersand", true, true)?.invokeStatic()
        }.getOrNull() ?: return null
        return runCatching { legacyAmpersand.invokeMethod<Any>("deserialize", text) }.getOrNull()
    }
}
