package org.tabooproject.baikiruto.core.version

import org.bukkit.Color
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.lang.reflect.Modifier
import java.util.Base64
import java.util.Locale
import java.util.UUID

abstract class BaseItemMetaVersionAdapter {

    protected open val supportsCustomModelData: Boolean = true

    private val enchantmentsByFieldName: Map<String, Enchantment> by lazy {
        Enchantment::class.java.fields
            .asSequence()
            .filter { field ->
                Modifier.isStatic(field.modifiers) && Enchantment::class.java.isAssignableFrom(field.type)
            }
            .mapNotNull { field ->
                runCatching { field.get(null) as? Enchantment }.getOrNull()?.let { enchantment ->
                    field.name.uppercase(Locale.ENGLISH) to enchantment
                }
            }
            .toMap()
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
        val potionTypeClass = runCatching { Class.forName("org.bukkit.potion.PotionType") }.getOrNull() ?: return
        val potionTypeName = baseTypeRaw.substringAfter(':').uppercase(Locale.ENGLISH).replace('-', '_')
        val potionType = resolveEnumConstant(potionTypeClass, potionTypeName) ?: return
        val setBasePotionType = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setBasePotionType" && method.parameterCount == 1
        }
        if (setBasePotionType != null && runCatching { setBasePotionType.invoke(itemMeta, potionType) }.isSuccess) {
            return
        }

        val potionDataClass = runCatching { Class.forName("org.bukkit.potion.PotionData") }.getOrNull() ?: return
        val extended = booleanValue(runtimeData["potion-base-extended"]) ?: false
        val upgraded = booleanValue(runtimeData["potion-base-upgraded"]) ?: false
        val potionData = createPotionData(potionDataClass, potionType, extended, upgraded) ?: return
        invokeObjectSetter(itemMeta, "setBasePotionData", potionData)
    }

    protected open fun applyAttributes(itemMeta: ItemMeta, rawAttributes: Any?) {
        val entries = rawAttributes as? Iterable<*> ?: return
        val attributeClass = runCatching { Class.forName("org.bukkit.attribute.Attribute") }.getOrNull() ?: return
        val modifierClass = runCatching { Class.forName("org.bukkit.attribute.AttributeModifier") }.getOrNull() ?: return
        val operationClass = runCatching { Class.forName("org.bukkit.attribute.AttributeModifier\$Operation") }.getOrNull() ?: return
        val slotClass = runCatching { Class.forName("org.bukkit.inventory.EquipmentSlot") }.getOrNull()
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
            val operation = resolveEnumConstant(operationClass, operationName) ?: return@forEach
            val slot = if (slotClass != null && slotName != null) {
                resolveEnumConstant(slotClass, slotName)
            } else {
                null
            }
            val modifier = createAttributeModifier(modifierClass, operation, amount, slot) ?: return@forEach
            runCatching { addMethod.invoke(itemMeta, attribute, modifier) }
        }
    }

    protected open fun applyItemModel(itemMeta: ItemMeta, modelId: String) {
        val setItemModel = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setItemModel" && method.parameterCount == 1
        } ?: return
        val namespacedKey = createNamespacedKey(modelId) ?: return
        runCatching { setItemModel.invoke(itemMeta, namespacedKey) }
    }

    protected open fun applyTooltipStyle(itemMeta: ItemMeta, styleId: String) {
        val setTooltipStyle = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setTooltipStyle" && method.parameterCount == 1
        } ?: return
        val namespacedKey = createNamespacedKey(styleId) ?: return
        runCatching { setTooltipStyle.invoke(itemMeta, namespacedKey) }
    }

    protected open fun applyRarity(itemMeta: ItemMeta, rarity: String) {
        val setRarity = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setRarity" && method.parameterCount == 1
        } ?: return
        val type = setRarity.parameterTypes[0]
        val normalized = rarity.trim().uppercase(Locale.ENGLISH).replace('-', '_')
        val constant = resolveEnumConstant(type, normalized) ?: return
        runCatching { setRarity.invoke(itemMeta, constant) }
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
            runCatching { addCustomEffect.invoke(itemMeta, effect, true) }
        }
    }

    protected open fun applySkullOwner(itemMeta: ItemMeta, owner: String) {
        val setOwner = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setOwner" && method.parameterCount == 1
        }
        if (setOwner != null && runCatching { setOwner.invoke(itemMeta, owner) }.isSuccess) {
            return
        }
        val setOwningPlayer = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setOwningPlayer" && method.parameterCount == 1
        } ?: return
        val offline = runCatching { Bukkit.getOfflinePlayer(owner) }.getOrNull() ?: return
        runCatching { setOwningPlayer.invoke(itemMeta, offline) }
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
        if (setProfile != null && runCatching { setProfile.invoke(itemMeta, profile) }.isSuccess) {
            return
        }
        val field = runCatching { itemMeta.javaClass.getDeclaredField("profile") }.getOrNull() ?: return
        runCatching {
            field.isAccessible = true
            field.set(itemMeta, profile)
        }
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
        val state = runCatching { getBlockState.invoke(itemMeta) }.getOrNull() ?: return

        runtimeData["spawner-entity"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { entityName ->
            val setSpawnedType = state.javaClass.methods.firstOrNull { method ->
                method.name == "setSpawnedType" && method.parameterCount == 1
            } ?: return@let
            val entityTypeClass = runCatching { Class.forName("org.bukkit.entity.EntityType") }.getOrNull() ?: return@let
            val normalized = entityName.substringAfter(':').uppercase(Locale.ENGLISH).replace('-', '_')
            val entityType = resolveEnumConstant(entityTypeClass, normalized) ?: return@let
            runCatching { setSpawnedType.invoke(state, entityType) }
        }

        applySpawnerInt(state, "setDelay", intValue(runtimeData["spawner-delay"]))
        applySpawnerInt(state, "setMinSpawnDelay", intValue(runtimeData["spawner-min-delay"]))
        applySpawnerInt(state, "setMaxSpawnDelay", intValue(runtimeData["spawner-max-delay"]))
        applySpawnerInt(state, "setSpawnCount", intValue(runtimeData["spawner-spawn-count"]))
        applySpawnerInt(state, "setMaxNearbyEntities", intValue(runtimeData["spawner-max-nearby-entities"]))
        applySpawnerInt(state, "setRequiredPlayerRange", intValue(runtimeData["spawner-required-player-range"]))
        applySpawnerInt(state, "setSpawnRange", intValue(runtimeData["spawner-spawn-range"]))

        runCatching { setBlockState.invoke(itemMeta, state) }
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

        val namespacedKeyClass = runCatching { Class.forName("org.bukkit.NamespacedKey") }.getOrNull()
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
        val namespacedKey = runCatching { constructor.newInstance(split[0], split[1]) }.getOrNull() ?: return null
        return runCatching { getByKey.invoke(null, namespacedKey) as? Enchantment }.getOrNull()
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
            return runCatching { ctor.newInstance(potionType, extended, upgraded) }.getOrNull()
        }
        potionDataClass.constructors.firstOrNull { ctor -> ctor.parameterCount == 1 }?.let { ctor ->
            return runCatching { ctor.newInstance(potionType) }.getOrNull()
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
            return runCatching {
                constructor.newInstance(effectType, duration, amplifier, ambient, particles, icon) as PotionEffect
            }.getOrNull()
        }
        return runCatching { PotionEffect(effectType, duration, amplifier, ambient, particles) }.getOrNull()
    }

    private fun createAttributeModifier(
        modifierClass: Class<*>,
        operation: Any,
        amount: Double,
        slot: Any?
    ): Any? {
        val constructors = modifierClass.constructors.toList()
        if (slot != null) {
            constructors.firstOrNull { it.parameterCount == 5 }?.let { ctor ->
                return runCatching {
                    ctor.newInstance(
                        UUID.randomUUID(),
                        "baikiruto.attr",
                        amount,
                        operation,
                        slot
                    )
                }.getOrNull()
            }
            constructors.firstOrNull { it.parameterCount == 4 }?.let { ctor ->
                return runCatching {
                    ctor.newInstance(
                        "baikiruto.attr",
                        amount,
                        operation,
                        slot
                    )
                }.getOrNull()
            }
        }
        constructors.firstOrNull { it.parameterCount == 3 }?.let { ctor ->
            return runCatching {
                ctor.newInstance(
                    "baikiruto.attr",
                    amount,
                    operation
                )
            }.getOrNull()
        }
        return null
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
        val profileClass = runCatching { Class.forName("com.mojang.authlib.GameProfile") }.getOrNull() ?: return null
        val propertyClass = runCatching { Class.forName("com.mojang.authlib.properties.Property") }.getOrNull() ?: return null
        val profile = runCatching {
            profileClass.getConstructor(UUID::class.java, String::class.java)
                .newInstance(UUID.randomUUID(), "baikiruto")
        }.getOrNull() ?: return null
        val property = runCatching {
            if (signature.isNullOrBlank()) {
                propertyClass.getConstructor(String::class.java, String::class.java)
                    .newInstance("textures", texture)
            } else {
                propertyClass.getConstructor(String::class.java, String::class.java, String::class.java)
                    .newInstance("textures", texture, signature)
            }
        }.getOrNull() ?: return null
        val getProperties = profileClass.methods.firstOrNull { method ->
            method.name == "getProperties" && method.parameterCount == 0
        } ?: return null
        val properties = runCatching { getProperties.invoke(profile) }.getOrNull() ?: return null
        val put = properties.javaClass.methods.firstOrNull { method ->
            method.name == "put" && method.parameterCount == 2
        } ?: return null
        runCatching { put.invoke(properties, "textures", property) }
        return profile
    }

    private fun invokeIntSetter(target: Any, name: String, value: Int): Boolean {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 } ?: return false
        return runCatching { method.invoke(target, value) }.isSuccess
    }

    private fun invokeBooleanSetter(target: Any, name: String, value: Boolean): Boolean {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 } ?: return false
        return runCatching { method.invoke(target, value) }.isSuccess
    }

    private fun invokeObjectSetter(target: Any, name: String, value: Any): Boolean {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == name &&
                method.parameterCount == 1 &&
                method.parameterTypes[0].isAssignableFrom(value.javaClass)
        } ?: return false
        return runCatching { method.invoke(target, value) }.isSuccess
    }

    private fun invokeCollectionSetter(target: Any, name: String, values: Collection<*>): Boolean {
        val method = target.javaClass.methods.firstOrNull { method ->
            method.name == name && method.parameterCount == 1
        } ?: return false
        return runCatching { method.invoke(target, values) }.isSuccess
    }

    private fun applySpawnerInt(state: Any, name: String, value: Int?) {
        if (value == null) {
            return
        }
        val method = state.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 1 } ?: return
        runCatching { method.invoke(state, value) }
    }

    private fun applyNamespacedKeyCollection(target: Any, methodName: String, values: Collection<String>) {
        val method = target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 1 } ?: return
        val keys = values.mapNotNull(::createNamespacedKey).toSet()
        if (keys.isEmpty()) {
            return
        }
        runCatching { method.invoke(target, keys) }
    }

    private fun createNamespacedKey(raw: String): Any? {
        val namespacedKeyClass = runCatching { Class.forName("org.bukkit.NamespacedKey") }.getOrNull() ?: return null
        val fromString = namespacedKeyClass.methods.firstOrNull { method ->
            method.name == "fromString" && method.parameterCount == 1
        }
        if (fromString != null) {
            return runCatching { fromString.invoke(null, raw) }.getOrNull()
        }
        val normalized = if (':' in raw) raw else "minecraft:$raw"
        val split = normalized.split(':', limit = 2)
        if (split.size != 2) {
            return null
        }
        val constructor = namespacedKeyClass.constructors.firstOrNull { it.parameterCount == 2 } ?: return null
        return runCatching { constructor.newInstance(split[0], split[1]) }.getOrNull()
    }

    private fun resolvePotionEffectType(raw: String): PotionEffectType? {
        val normalized = raw.uppercase(Locale.ENGLISH).replace('-', '_')
        return PotionEffectType.getByName(normalized)
            ?: resolvePotionEffectTypeByKey(raw)
    }

    private fun resolvePotionEffectTypeByKey(raw: String): PotionEffectType? {
        val namespacedKeyClass = runCatching { Class.forName("org.bukkit.NamespacedKey") }.getOrNull() ?: return null
        val getByKey = PotionEffectType::class.java.methods.firstOrNull { method ->
            method.name == "getByKey" && method.parameterCount == 1
        } ?: return null
        val fromString = namespacedKeyClass.methods.firstOrNull { method ->
            method.name == "fromString" && method.parameterCount == 1
        } ?: return null
        val normalized = if (':' in raw) raw else "minecraft:${raw.lowercase(Locale.ENGLISH)}"
        val key = runCatching { fromString.invoke(null, normalized) }.getOrNull() ?: return null
        return runCatching { getByKey.invoke(null, key) as? PotionEffectType }.getOrNull()
    }
}
