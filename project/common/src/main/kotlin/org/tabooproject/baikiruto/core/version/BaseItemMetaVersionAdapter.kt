package org.tabooproject.baikiruto.core.version

import org.bukkit.Color
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.tabooproject.baikiruto.core.item.Attributes
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import taboolib.library.reflex.LazyClass
import taboolib.library.reflex.Reflex.Companion.setProperty
import taboolib.library.reflex.ReflexClass
import java.util.Base64
import java.util.Locale
import java.util.UUID

abstract class BaseItemMetaVersionAdapter {

    protected open val supportsCustomModelData: Boolean = true

    private val enchantmentsByFieldName: Map<String, Enchantment> by lazy {
        buildMap {
            Enchantment.values().forEach { enchantment ->
                runCatching { put(enchantment.name.uppercase(Locale.ENGLISH), enchantment) }
                runCatching { put(enchantment.key.key.uppercase(Locale.ENGLISH), enchantment) }
            }
        }
    }

    private fun resolveClass(name: String): Class<*>? {
        return runCatching {
            LazyClass.of(source = name, dimensions = 0, isPrimitive = false, classFinder = null).instance
        }.getOrNull()
    }

    private fun invokeWithReflex(target: Any, method: Method, vararg args: Any?): Any? {
        val classMethod = runCatching {
            ReflexClass.of(target.javaClass).getMethodByTypeSilently(
                method.name,
                true,
                true,
                *method.parameterTypes
            )
        }.getOrNull() ?: return null
        return runCatching { classMethod.invoke(target, *args) }.getOrNull()
    }

    private fun invokeWithReflexSucceeded(target: Any, method: Method, vararg args: Any?): Boolean {
        val classMethod = runCatching {
            ReflexClass.of(target.javaClass).getMethodByTypeSilently(
                method.name,
                true,
                true,
                *method.parameterTypes
            )
        }.getOrNull() ?: return false
        return runCatching {
            classMethod.invoke(target, *args)
            true
        }.getOrDefault(false)
    }

    private fun invokeStaticWithReflex(owner: Class<*>, method: Method, vararg args: Any?): Any? {
        val classMethod = runCatching {
            ReflexClass.of(owner).getMethodByTypeSilently(
                method.name,
                true,
                true,
                *method.parameterTypes
            )
        }.getOrNull() ?: return null
        return runCatching { classMethod.invokeStatic(*args) }.getOrNull()
    }

    private fun invokeConstructorWithReflex(constructor: Constructor<*>, vararg args: Any?): Any? {
        val classConstructor = runCatching {
            ReflexClass.of(constructor.declaringClass).getConstructorByTypeSilently(*constructor.parameterTypes)
        }.getOrNull() ?: return null
        return runCatching { classConstructor.instance(*args) }.getOrNull()
    }

    open fun applyDisplayName(itemStack: ItemStack, displayName: String?) {
        val itemMeta = itemStack.itemMeta ?: return
        itemMeta.setDisplayName(displayName)
        itemStack.itemMeta = itemMeta
    }

    open fun applyLore(itemStack: ItemStack, lore: List<String>) {
        val itemMeta = itemStack.itemMeta ?: return
        itemMeta.lore = lore.toMutableList()
        itemStack.itemMeta = itemMeta
    }

    open fun readItemData(itemStack: ItemStack): Map<String, Any?> {
        return itemStack.serialize()
    }

    open fun applyVersionEffects(itemStack: ItemStack, runtimeData: Map<String, Any?>) {
        val damage = intValue(runtimeData["damage"]) ?: intValue(runtimeData["legacy-durability"])
        if (damage != null) {
            applyDamage(itemStack, damage)
        }

        val itemMeta = itemStack.itemMeta ?: return
        applyEnchantments(itemMeta, runtimeData["enchantments"])

        if (booleanValue(runtimeData["glow"]) == true) {
            applyGlow(itemMeta)
        }

        if (supportsCustomModelData) {
            val customModelData = intValue(runtimeData["custom-model-data"]) ?: intValue(runtimeData["custommodeldata"])
            if (customModelData != null) {
                applyCustomModelData(itemMeta, customModelData)
            }
        }

        booleanValue(runtimeData["unbreakable"])?.let {
            applyUnbreakable(itemMeta, it)
        }

        applyItemFlags(
            itemMeta,
            runtimeData["item-flags"]
                ?: runtimeData["itemflags"]
                ?: runtimeData["hide-flags"]
                ?: runtimeData["hideflags"]
        )

        colorValue(runtimeData["color"])?.let { applyColor(itemMeta, it) }
        colorValue(runtimeData["potion-color"] ?: runtimeData["potioncolor"])?.let { applyPotionColor(itemMeta, it) }
        applyPotionBase(itemMeta, runtimeData)
        applyAttributes(itemMeta, runtimeData["attributes"])
        runtimeData["item-model"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            applyItemModel(itemMeta, it)
        }
        applyCanDestroy(itemMeta, runtimeData["can-destroy"])
        applyCanPlaceOn(itemMeta, runtimeData["can-place-on"])
        runtimeData["tooltip-style"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            applyTooltipStyle(itemMeta, it)
        }
        runtimeData["rarity"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            applyRarity(itemMeta, it)
        }
        booleanValue(runtimeData["glider"])?.let {
            applyGlider(itemMeta, it)
        }
        applyPotionEffects(itemMeta, runtimeData["potion-effects"])
        runtimeData["skull-owner"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            applySkullOwner(itemMeta, it)
        }
        applySkullTexture(itemMeta, runtimeData)
        applySpawnerSettings(itemMeta, runtimeData)
        itemStack.itemMeta = itemMeta
    }

    protected open fun applyDamage(itemStack: ItemStack, damage: Int) {
        val safeDamage = damage.coerceAtLeast(0)
        val itemMeta = itemStack.itemMeta
        if (itemMeta != null && invokeIntSetter(itemMeta, "setDamage", safeDamage)) {
            itemStack.itemMeta = itemMeta
            return
        }
        runCatching { itemStack.durability = safeDamage.toShort() }
    }

    protected open fun applyEnchantments(itemMeta: ItemMeta, rawEnchantments: Any?) {
        parseEnchantments(rawEnchantments).forEach { (enchantment, level) ->
            itemMeta.addEnchant(enchantment, level, true)
        }
    }

    protected open fun applyGlow(itemMeta: ItemMeta) {
        val enchantment = resolveEnchantment("DURABILITY") ?: resolveEnchantment("UNBREAKING") ?: return
        if (!itemMeta.hasEnchant(enchantment)) {
            itemMeta.addEnchant(enchantment, 1, true)
        }
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
    }

    protected open fun applyCustomModelData(itemMeta: ItemMeta, customModelData: Int) {
        invokeIntSetter(itemMeta, "setCustomModelData", customModelData)
    }

    protected open fun applyUnbreakable(itemMeta: ItemMeta, unbreakable: Boolean) {
        invokeBooleanSetter(itemMeta, "setUnbreakable", unbreakable)
    }

    protected open fun applyItemFlags(itemMeta: ItemMeta, rawFlags: Any?) {
        val flags = stringList(rawFlags)
            .mapNotNull { name ->
                runCatching { ItemFlag.valueOf(name.uppercase(Locale.ENGLISH).replace('-', '_')) }.getOrNull()
            }
            .toTypedArray()
        if (flags.isNotEmpty()) {
            itemMeta.addItemFlags(*flags)
        }
    }

    protected open fun applyColor(itemMeta: ItemMeta, rgb: Int) {
        invokeObjectSetter(itemMeta, "setColor", Color.fromRGB(rgb and 0xFFFFFF))
    }

    protected open fun applyPotionColor(itemMeta: ItemMeta, rgb: Int) {
        invokeObjectSetter(itemMeta, "setColor", Color.fromRGB(rgb and 0xFFFFFF))
    }

    protected open fun applyPotionBase(itemMeta: ItemMeta, runtimeData: Map<String, Any?>) {
        val baseTypeRaw = runtimeData["potion-base-type"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val potionTypeClass = resolveClass("org.bukkit.potion.PotionType") ?: return
        val potionTypeName = baseTypeRaw.substringAfter(':').uppercase(Locale.ENGLISH).replace('-', '_')
        val potionType = resolveEnumConstant(potionTypeClass, potionTypeName) ?: return
        val setBasePotionType = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setBasePotionType" && method.parameterCount == 1
        }
        if (setBasePotionType != null && invokeWithReflexSucceeded(itemMeta, setBasePotionType, potionType)) {
            return
        }

        val potionDataClass = resolveClass("org.bukkit.potion.PotionData") ?: return
        val extended = booleanValue(runtimeData["potion-base-extended"]) ?: false
        val upgraded = booleanValue(runtimeData["potion-base-upgraded"]) ?: false
        val potionData = createPotionData(potionDataClass, potionType, extended, upgraded) ?: return
        invokeObjectSetter(itemMeta, "setBasePotionData", potionData)
    }

    protected open fun applyAttributes(itemMeta: ItemMeta, rawAttributes: Any?) {
        val entries = rawAttributes as? Iterable<*> ?: return
        val attributeClass = resolveClass("org.bukkit.attribute.Attribute") ?: return
        val addMethod = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "addAttributeModifier" && method.parameterCount == 2
        } ?: return
        entries.forEach { rawEntry ->
            val entry = rawEntry as? Map<*, *> ?: return@forEach
            val attributeName = entry["attribute"]?.toString()?.trim()?.uppercase(Locale.ENGLISH) ?: return@forEach
            val amount = doubleValue(entry["amount"]) ?: return@forEach
            val operationName = entry["operation"]?.toString()?.trim()?.uppercase(Locale.ENGLISH)
                ?: "ADD_NUMBER"
            val slotName = entry["slot"]?.toString()?.trim()?.uppercase(Locale.ENGLISH)

            val attribute = resolveEnumConstant(attributeClass, attributeName) ?: return@forEach
            val operation = runCatching { AttributeModifier.Operation.valueOf(operationName) }.getOrNull()
                ?: return@forEach
            val slot = slotName?.let { rawSlot ->
                runCatching { EquipmentSlot.valueOf(rawSlot) }.getOrNull()
            }
            val modifier = Attributes.createAttributeModifier(
                name = "baikiruto.attr",
                amount = amount,
                operation = operation,
                equipmentSlot = slot
            ) ?: return@forEach
            invokeWithReflexSucceeded(itemMeta, addMethod, attribute, modifier)
        }
    }

    protected open fun applyItemModel(itemMeta: ItemMeta, modelId: String) {
        val setItemModel = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setItemModel" && method.parameterCount == 1
        } ?: return
        val namespacedKey = createNamespacedKey(modelId) ?: return
        invokeWithReflexSucceeded(itemMeta, setItemModel, namespacedKey)
    }

    protected open fun applyTooltipStyle(itemMeta: ItemMeta, styleId: String) {
        val setTooltipStyle = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setTooltipStyle" && method.parameterCount == 1
        } ?: return
        val namespacedKey = createNamespacedKey(styleId) ?: return
        invokeWithReflexSucceeded(itemMeta, setTooltipStyle, namespacedKey)
    }

    protected open fun applyRarity(itemMeta: ItemMeta, rarity: String) {
        val setRarity = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setRarity" && method.parameterCount == 1
        } ?: return
        val type = setRarity.parameterTypes[0]
        val normalized = rarity.trim().uppercase(Locale.ENGLISH).replace('-', '_')
        val constant = resolveEnumConstant(type, normalized) ?: return
        invokeWithReflexSucceeded(itemMeta, setRarity, constant)
    }

    protected open fun applyGlider(itemMeta: ItemMeta, enabled: Boolean) {
        invokeBooleanSetter(itemMeta, "setGlider", enabled)
    }

    protected open fun applyCanDestroy(itemMeta: ItemMeta, raw: Any?) {
        val materials = parseMaterials(raw)
        if (materials.isEmpty()) {
            return
        }
        if (invokeCollectionSetter(itemMeta, "setCanDestroy", materials)) {
            return
        }
        val keys = materials.map { "minecraft:${it.name.lowercase(Locale.ENGLISH)}" }
        applyNamespacedKeyCollection(itemMeta, "setDestroyableKeys", keys)
    }

    protected open fun applyCanPlaceOn(itemMeta: ItemMeta, raw: Any?) {
        val materials = parseMaterials(raw)
        if (materials.isEmpty()) {
            return
        }
        if (invokeCollectionSetter(itemMeta, "setCanPlaceOn", materials)) {
            return
        }
        val keys = materials.map { "minecraft:${it.name.lowercase(Locale.ENGLISH)}" }
        applyNamespacedKeyCollection(itemMeta, "setPlaceableKeys", keys)
    }

    protected open fun applyPotionEffects(itemMeta: ItemMeta, raw: Any?) {
        val effects = raw as? Iterable<*> ?: return
        val addCustomEffect = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "addCustomEffect" && method.parameterCount == 2
        } ?: return
        effects.forEach { rawEntry ->
            val entry = rawEntry as? Map<*, *> ?: return@forEach
            val typeName = entry["type"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val effectType = resolvePotionEffectType(typeName) ?: return@forEach
            val duration = intValue(entry["duration"])?.coerceAtLeast(1) ?: 200
            val amplifier = intValue(entry["amplifier"])?.coerceAtLeast(0) ?: 0
            val ambient = booleanValue(entry["ambient"]) ?: false
            val particles = booleanValue(entry["particles"]) ?: true
            val icon = booleanValue(entry["icon"]) ?: true
            val effect = createPotionEffect(effectType, duration, amplifier, ambient, particles, icon) ?: return@forEach
            invokeWithReflexSucceeded(itemMeta, addCustomEffect, effect, true)
        }
    }

    protected open fun applySkullOwner(itemMeta: ItemMeta, owner: String) {
        val setOwner = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setOwner" && method.parameterCount == 1
        }
        if (setOwner != null && invokeWithReflexSucceeded(itemMeta, setOwner, owner)) {
            return
        }
        val setOwningPlayer = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setOwningPlayer" && method.parameterCount == 1
        } ?: return
        val offline = runCatching { Bukkit.getOfflinePlayer(owner) }.getOrNull() ?: return
        invokeWithReflexSucceeded(itemMeta, setOwningPlayer, offline)
    }

    protected open fun applySkullTexture(itemMeta: ItemMeta, runtimeData: Map<String, Any?>) {
        val texture = runtimeData["skull-texture"]?.toString()?.trim()
        val url = runtimeData["skull-url"]?.toString()?.trim()
        val signature = runtimeData["skull-signature"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val textureValue = when {
            !texture.isNullOrBlank() -> normalizeSkullTexture(texture)
            !url.isNullOrBlank() -> encodeSkullUrl(url)
            else -> null
        } ?: return
        val profile = createGameProfile(textureValue, signature) ?: return

        val setProfile = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setProfile" && method.parameterCount == 1
        }
        if (setProfile != null && invokeWithReflexSucceeded(itemMeta, setProfile, profile)) {
            return
        }
        runCatching { itemMeta.setProperty("profile", profile) }
    }

    protected open fun applySpawnerType(itemMeta: ItemMeta, entityName: String) {
        applySpawnerSettings(itemMeta, mapOf("spawner-entity" to entityName))
    }

    protected open fun applySpawnerSettings(itemMeta: ItemMeta, runtimeData: Map<String, Any?>) {
        val getBlockState = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "getBlockState" && method.parameterCount == 0
        } ?: return
        val setBlockState = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setBlockState" && method.parameterCount == 1
        } ?: return
        val state = invokeWithReflex(itemMeta, getBlockState) ?: return

        runtimeData["spawner-entity"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { entityName ->
            val setSpawnedType = state.javaClass.methods.firstOrNull { method ->
                method.name == "setSpawnedType" && method.parameterCount == 1
            } ?: return@let
            val entityTypeClass = resolveClass("org.bukkit.entity.EntityType") ?: return@let
            val normalized = entityName.substringAfter(':').uppercase(Locale.ENGLISH).replace('-', '_')
            val entityType = resolveEnumConstant(entityTypeClass, normalized) ?: return@let
            invokeWithReflexSucceeded(state, setSpawnedType, entityType)
        }

        applySpawnerInt(state, "setDelay", intValue(runtimeData["spawner-delay"]))
        applySpawnerInt(state, "setMinSpawnDelay", intValue(runtimeData["spawner-min-delay"]))
        applySpawnerInt(state, "setMaxSpawnDelay", intValue(runtimeData["spawner-max-delay"]))
        applySpawnerInt(state, "setSpawnCount", intValue(runtimeData["spawner-spawn-count"]))
        applySpawnerInt(state, "setMaxNearbyEntities", intValue(runtimeData["spawner-max-nearby-entities"]))
        applySpawnerInt(state, "setRequiredPlayerRange", intValue(runtimeData["spawner-required-player-range"]))
        applySpawnerInt(state, "setSpawnRange", intValue(runtimeData["spawner-spawn-range"]))

        invokeWithReflexSucceeded(itemMeta, setBlockState, state)
    }

    protected fun intValue(rawValue: Any?): Int? {
        return when (rawValue) {
            is Number -> rawValue.toInt()
            is String -> rawValue.trim().toIntOrNull()
            else -> null
        }
    }

    protected fun booleanValue(rawValue: Any?): Boolean? {
        return when (rawValue) {
            is Boolean -> rawValue
            is Number -> rawValue.toInt() != 0
            is String -> when (rawValue.trim().lowercase(Locale.ENGLISH)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }

    protected fun doubleValue(rawValue: Any?): Double? {
        return when (rawValue) {
            is Number -> rawValue.toDouble()
            is String -> rawValue.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun colorValue(rawValue: Any?): Int? {
        return when (rawValue) {
            is Number -> rawValue.toInt().coerceIn(0, 0xFFFFFF)
            is String -> {
                val normalized = rawValue.trim()
                    .removePrefix("#")
                    .removePrefix("0x")
                    .removePrefix("0X")
                normalized.toIntOrNull(16)?.coerceIn(0, 0xFFFFFF)
            }
            else -> null
        }
    }

    private fun parseEnchantments(rawEnchantments: Any?): Map<Enchantment, Int> {
        val parsed = linkedMapOf<Enchantment, Int>()
        when (rawEnchantments) {
            is Map<*, *> -> {
                rawEnchantments.forEach { (rawId, rawLevel) ->
                    val enchantment = resolveEnchantment(rawId?.toString()) ?: return@forEach
                    val level = intValue(rawLevel) ?: return@forEach
                    if (level > 0) {
                        parsed[enchantment] = level
                    }
                }
            }
            is Iterable<*> -> {
                rawEnchantments.forEach { rawLine ->
                    val line = rawLine?.toString()?.trim().orEmpty()
                    if (line.isBlank()) {
                        return@forEach
                    }
                    val delimiter = line.indexOf(':').takeIf { it >= 0 } ?: line.indexOf('=').takeIf { it >= 0 }
                    if (delimiter == null) {
                        return@forEach
                    }
                    val enchantment = resolveEnchantment(line.substring(0, delimiter).trim()) ?: return@forEach
                    val level = line.substring(delimiter + 1).trim().toIntOrNull() ?: return@forEach
                    if (level > 0) {
                        parsed[enchantment] = level
                    }
                }
            }
        }
        return parsed
    }

    private fun resolveEnchantment(rawId: String?): Enchantment? {
        if (rawId.isNullOrBlank()) {
            return null
        }
        val normalized = rawId.trim()
        val upper = normalized.uppercase(Locale.ENGLISH).replace('-', '_')
        return Enchantment.getByName(upper)
            ?: enchantmentsByFieldName[upper]
            ?: resolveEnchantmentByKey(normalized)
    }

    private fun resolveEnchantmentByKey(rawId: String): Enchantment? {
        val getByKey = Enchantment::class.java.methods.firstOrNull { method ->
            method.name == "getByKey" && method.parameterCount == 1
        } ?: return null

        val namespacedKeyClass = resolveClass("org.bukkit.NamespacedKey")
            ?: return null
        val constructor = namespacedKeyClass.constructors.firstOrNull { ctor ->
            ctor.parameterCount == 2 &&
                ctor.parameterTypes[0] == String::class.java &&
                ctor.parameterTypes[1] == String::class.java
        } ?: return null

        val normalized = if (rawId.contains(':')) rawId else "minecraft:${rawId.lowercase(Locale.ENGLISH)}"
        val split = normalized.split(':', limit = 2)
        if (split.size != 2) {
            return null
        }
        val namespacedKey = invokeConstructorWithReflex(constructor, split[0], split[1]) ?: return null
        return invokeStaticWithReflex(Enchantment::class.java, getByKey, namespacedKey) as? Enchantment
    }

    private fun stringList(rawValue: Any?): List<String> {
        return when (rawValue) {
            is String -> rawValue.split(',').map { it.trim() }.filter { it.isNotBlank() }
            is Iterable<*> -> rawValue.mapNotNull { value ->
                value?.toString()?.trim()?.takeIf { it.isNotBlank() }
            }
            else -> emptyList()
        }
    }

    private fun parseMaterials(rawValue: Any?): List<Material> {
        return stringList(rawValue)
            .mapNotNull { token ->
                Material.matchMaterial(token)
                    ?: Material.matchMaterial(token.uppercase(Locale.ENGLISH))
            }
    }

    private fun createPotionData(
        potionDataClass: Class<*>,
        potionType: Any,
        extended: Boolean,
        upgraded: Boolean
    ): Any? {
        potionDataClass.constructors.firstOrNull { ctor -> ctor.parameterCount == 3 }?.let { ctor ->
            return invokeConstructorWithReflex(ctor, potionType, extended, upgraded)
        }
        potionDataClass.constructors.firstOrNull { ctor -> ctor.parameterCount == 1 }?.let { ctor ->
            return invokeConstructorWithReflex(ctor, potionType)
        }
        return null
    }

    private fun createPotionEffect(
        effectType: PotionEffectType,
        duration: Int,
        amplifier: Int,
        ambient: Boolean,
        particles: Boolean,
        icon: Boolean
    ): PotionEffect? {
        val constructor = PotionEffect::class.java.constructors.firstOrNull { ctor ->
            ctor.parameterCount == 6
        }
        if (constructor != null) {
            return invokeConstructorWithReflex(
                constructor,
                effectType,
                duration,
                amplifier,
                ambient,
                particles,
                icon
            ) as? PotionEffect
        }
        return runCatching { PotionEffect(effectType, duration, amplifier, ambient, particles) }.getOrNull()
    }

    private fun resolveEnumConstant(enumClass: Class<*>, name: String): Any? {
        return enumClass.enumConstants?.firstOrNull { constant ->
            (constant as? Enum<*>)?.name == name
        }
    }

    private fun normalizeSkullTexture(raw: String): String {
        val value = raw.trim()
        if (value.startsWith("{") || value.startsWith("http://") || value.startsWith("https://")) {
            return encodeSkullUrl(value)
        }
        return value
    }

    private fun encodeSkullUrl(raw: String): String {
        val payload = if (raw.trim().startsWith("{")) {
            raw.trim()
        } else {
            "{\"textures\":{\"SKIN\":{\"url\":\"${raw.trim()}\"}}}"
        }
        return Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
    }

    private fun createGameProfile(texture: String, signature: String?): Any? {
        val profileClass = resolveClass("com.mojang.authlib.GameProfile") ?: return null
        val propertyClass = resolveClass("com.mojang.authlib.properties.Property") ?: return null
        val profile = runCatching {
            profileClass.getConstructor(UUID::class.java, String::class.java)
                .let { constructor -> invokeConstructorWithReflex(constructor, UUID.randomUUID(), "baikiruto") }
        }.getOrNull() ?: return null
        val property = runCatching {
            if (signature.isNullOrBlank()) {
                propertyClass.getConstructor(String::class.java, String::class.java)
                    .let { constructor -> invokeConstructorWithReflex(constructor, "textures", texture) }
            } else {
                propertyClass.getConstructor(String::class.java, String::class.java, String::class.java)
                    .let { constructor -> invokeConstructorWithReflex(constructor, "textures", texture, signature) }
            }
        }.getOrNull() ?: return null
        val getProperties = profileClass.methods.firstOrNull { method ->
            method.name == "getProperties" && method.parameterCount == 0
        } ?: return null
        val properties = invokeWithReflex(profile, getProperties) ?: return null
        val put = properties.javaClass.methods.firstOrNull { method ->
            method.name == "put" && method.parameterCount == 2
        } ?: return null
        invokeWithReflexSucceeded(properties, put, "textures", property)
        return profile
    }

    private fun invokeIntSetter(target: Any, name: String, value: Int): Boolean {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 } ?: return false
        return invokeWithReflexSucceeded(target, method, value)
    }

    private fun invokeBooleanSetter(target: Any, name: String, value: Boolean): Boolean {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 } ?: return false
        return invokeWithReflexSucceeded(target, method, value)
    }

    private fun invokeObjectSetter(target: Any, name: String, value: Any): Boolean {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == name &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isAssignableFrom(value.javaClass)
        } ?: return false
        return invokeWithReflexSucceeded(target, method, value)
    }

    private fun invokeCollectionSetter(target: Any, name: String, values: Collection<*>): Boolean {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == name && method.parameterCount == 1
        } ?: return false
        return invokeWithReflexSucceeded(target, method, values)
    }

    private fun applySpawnerInt(state: Any, name: String, value: Int?) {
        if (value == null) {
            return
        }
        val method = state.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 } ?: return
        invokeWithReflexSucceeded(state, method, value)
    }

    private fun applyNamespacedKeyCollection(target: Any, methodName: String, values: Collection<String>) {
        val method = target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 1 } ?: return
        val keys = values.mapNotNull(::createNamespacedKey).toSet()
        if (keys.isEmpty()) {
            return
        }
        invokeWithReflexSucceeded(target, method, keys)
    }

    private fun createNamespacedKey(raw: String): Any? {
        val namespacedKeyClass = resolveClass("org.bukkit.NamespacedKey") ?: return null
        val fromString = namespacedKeyClass.methods.firstOrNull { method ->
            method.name == "fromString" && method.parameterCount == 1
        }
        if (fromString != null) {
            return invokeStaticWithReflex(namespacedKeyClass, fromString, raw)
        }
        val normalized = if (':' in raw) raw else "minecraft:$raw"
        val split = normalized.split(':', limit = 2)
        if (split.size != 2) {
            return null
        }
        val constructor = namespacedKeyClass.constructors.firstOrNull { it.parameterCount == 2 } ?: return null
        return invokeConstructorWithReflex(constructor, split[0], split[1])
    }

    private fun resolvePotionEffectType(raw: String): PotionEffectType? {
        val normalized = raw.uppercase(Locale.ENGLISH).replace('-', '_')
        return PotionEffectType.getByName(normalized)
            ?: resolvePotionEffectTypeByKey(raw)
    }

    private fun resolvePotionEffectTypeByKey(raw: String): PotionEffectType? {
        val namespacedKeyClass = resolveClass("org.bukkit.NamespacedKey") ?: return null
        val getByKey = PotionEffectType::class.java.methods.firstOrNull { method ->
            method.name == "getByKey" && method.parameterCount == 1
        } ?: return null
        val fromString = namespacedKeyClass.methods.firstOrNull { method ->
            method.name == "fromString" && method.parameterCount == 1
        } ?: return null
        val normalized = if (':' in raw) raw else "minecraft:${raw.lowercase(Locale.ENGLISH)}"
        val key = invokeStaticWithReflex(namespacedKeyClass, fromString, normalized) ?: return null
        return invokeStaticWithReflex(PotionEffectType::class.java, getByKey, key) as? PotionEffectType
    }
}
