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
import taboolib.common.platform.function.info
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

    /**
     * Debug 日志开关，子类可覆盖以接入实际的 debug 配置。
     * 默认通过系统属性 `baikiruto.debug` 控制。
     */
    protected open val debugEnabled: Boolean
        get() = System.getProperty("baikiruto.debug", "false").equals("true", ignoreCase = true)

    protected fun debugLog(message: String) {
        if (debugEnabled) {
            info(message)
        }
    }

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
        debugLog("[Baikiruto/Debug] applyVersionEffects: runtimeData keys=${runtimeData.keys}")
        debugLog("[Baikiruto/Debug] applyVersionEffects: attributes=${runtimeData["attributes"]}")
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
        val replaceAttributes = booleanValue(
            runtimeData["attributes-replace-mode"]
                ?: runtimeData["attributes-replace"]
        ) ?: false
        applyAttributes(itemMeta, runtimeData["attributes"], replaceAttributes)
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

    protected open fun applyAttributes(itemMeta: ItemMeta, rawAttributes: Any?, replaceAll: Boolean = false) {
        if (rawAttributes == null) {
            debugLog("[Baikiruto/Debug] applyAttributes: rawAttributes is null, skipping")
            return
        }
        val entries = rawAttributes as? Iterable<*>
        if (entries == null) {
            debugLog("[Baikiruto/Debug] applyAttributes: rawAttributes is not Iterable (type=${rawAttributes.javaClass.name}), skipping")
            return
        }
        val attributeClass = resolveClass("org.bukkit.attribute.Attribute")
        if (attributeClass == null) {
            debugLog("[Baikiruto/Debug] applyAttributes: org.bukkit.attribute.Attribute class not found, skipping")
            return
        }
        val addMethod = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "addAttributeModifier" && method.parameterCount == 2
        }
        if (addMethod == null) {
            debugLog("[Baikiruto/Debug] applyAttributes: addAttributeModifier method not found on ${itemMeta.javaClass.name}, skipping")
            return
        }

        // 清除已有的 attribute modifiers，防止重复构建时叠加
        // replaceAll=true: 清除全部（包括原版属性）
        // replaceAll=false: 仅清除 Baikiruto 添加的 modifiers，保留原版属性
        clearExistingAttributeModifiers(itemMeta, replaceAll)
        debugLog("[Baikiruto/Debug] applyAttributes: replaceAll=$replaceAll")

        debugLog("[Baikiruto/Debug] applyAttributes: processing ${entries.count()} entries")
        entries.forEach { rawEntry ->
            val entry = rawEntry as? Map<*, *>
            if (entry == null) {
                debugLog("[Baikiruto/Debug]   entry is not a Map (type=${rawEntry?.javaClass?.name}), skipping")
                return@forEach
            }
            val attributeName = entry["attribute"]?.toString()?.trim()?.uppercase(Locale.ENGLISH)
            if (attributeName == null) {
                debugLog("[Baikiruto/Debug]   entry missing 'attribute' key, entry=$entry, skipping")
                return@forEach
            }
            val amount = doubleValue(entry["amount"])
            if (amount == null) {
                debugLog("[Baikiruto/Debug]   attr=$attributeName -> amount is null (raw=${entry["amount"]}), skipping")
                return@forEach
            }
            val operationName = entry["operation"]?.toString()?.trim()?.uppercase(Locale.ENGLISH)
                ?: "ADD_NUMBER"
            val slotName = entry["slot"]?.toString()?.trim()?.uppercase(Locale.ENGLISH)

            val attribute = resolveAttributeConstant(attributeClass, attributeName)
            if (attribute == null) {
                debugLog("[Baikiruto/Debug]   attr=$attributeName -> attribute constant not found after all fallback attempts, skipping")
                return@forEach
            }
            val operation = runCatching { AttributeModifier.Operation.valueOf(operationName) }.getOrNull()
            if (operation == null) {
                debugLog("[Baikiruto/Debug]   attr=$attributeName -> operation '$operationName' not found (available: ${AttributeModifier.Operation.values().map { it.name }}), skipping")
                return@forEach
            }
            val slot = slotName?.let { rawSlot ->
                runCatching { EquipmentSlot.valueOf(rawSlot) }.getOrNull()
            }
            if (slotName != null && slot == null) {
                debugLog("[Baikiruto/Debug]   attr=$attributeName -> slot '$slotName' not found (available: ${EquipmentSlot.values().map { it.name }}), skipping")
                return@forEach
            }
            // 使用确定性 key：基于 attribute 名和 slot，避免随机 UUID 导致重复叠加
            val modifierKey = buildDeterministicModifierKey(attributeName, slotName)
            debugLog("[Baikiruto/Debug]   attr=$attributeName, amount=$amount, operation=$operation, slot=$slot, key=$modifierKey -> creating modifier")
            val modifier = Attributes.createAttributeModifier(
                name = modifierKey,
                amount = amount,
                operation = operation,
                equipmentSlot = slot
            )
            if (modifier == null) {
                debugLog("[Baikiruto/Debug]   attr=$attributeName -> Attributes.createAttributeModifier returned null!")
                return@forEach
            }
            debugLog("[Baikiruto/Debug]   attr=$attributeName -> modifier created: $modifier, invoking addAttributeModifier")
            val success = invokeWithReflexSucceeded(itemMeta, addMethod, attribute, modifier)
            debugLog("[Baikiruto/Debug]   attr=$attributeName -> addAttributeModifier result: $success")
        }
    }

    /**
     * 清除 ItemMeta 上已有的 attribute modifiers，防止物品重复构建时 modifier 不断叠加。
     *
     * @param replaceAll true = 清除全部 modifiers（包括原版属性），false = 仅清除 Baikiruto 添加的 modifiers
     */
    private fun clearExistingAttributeModifiers(itemMeta: ItemMeta, replaceAll: Boolean) {
        // 优先尝试 1.13.2+ 的 getAttributeModifiers() 无参方法
        val getModifiers = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "getAttributeModifiers" && method.parameterCount == 0
        }
        val removeMethod = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "removeAttributeModifier" && method.parameterCount == 2
        }
        if (getModifiers != null && removeMethod != null) {
            val multimap = invokeWithReflex(itemMeta, getModifiers)
            if (multimap != null) {
                // Multimap<Attribute, AttributeModifier> -> entries()
                val entriesMethod = multimap.javaClass.methods.firstOrNull { method ->
                    method.name == "entries" && method.parameterCount == 0
                }
                if (entriesMethod != null) {
                    val entries = invokeWithReflex(multimap, entriesMethod) as? Collection<*>
                    var removedCount = 0
                    entries?.toList()?.forEach { entry ->
                        // Map.Entry<Attribute, AttributeModifier>
                        val getKey = entry?.javaClass?.methods?.firstOrNull { it.name == "getKey" && it.parameterCount == 0 }
                        val getValue = entry?.javaClass?.methods?.firstOrNull { it.name == "getValue" && it.parameterCount == 0 }
                        if (getKey != null && getValue != null && entry != null) {
                            val attr = invokeWithReflex(entry, getKey)
                            val mod = invokeWithReflex(entry, getValue)
                            if (attr != null && mod != null) {
                                if (replaceAll || isBaikirutoModifier(mod)) {
                                    invokeWithReflexSucceeded(itemMeta, removeMethod, attr, mod)
                                    removedCount++
                                }
                            }
                        }
                    }
                    debugLog("[Baikiruto/Debug] applyAttributes: cleared $removedCount/${entries?.size ?: 0} existing attribute modifiers (replaceAll=$replaceAll)")
                    return
                }
            }
        }

        // 回退：replaceAll 模式下尝试按 EquipmentSlot 逐个清除
        // 非 replaceAll 模式下无法精确区分，跳过清除（依赖确定性 key 去重）
        if (replaceAll) {
            val removeBySlot = itemMeta.javaClass.methods.firstOrNull { method ->
                method.name == "removeAttributeModifier" && method.parameterCount == 1 &&
                    method.parameterTypes[0] == EquipmentSlot::class.java
            }
            if (removeBySlot != null) {
                EquipmentSlot.values().forEach { slot ->
                    runCatching { invokeWithReflexSucceeded(itemMeta, removeBySlot, slot) }
                }
                debugLog("[Baikiruto/Debug] applyAttributes: cleared existing attribute modifiers via slot-based removal (replaceAll)")
                return
            }
        }

        debugLog("[Baikiruto/Debug] applyAttributes: ${if (replaceAll) "no method available to clear" else "non-replace mode, relying on deterministic keys"}")
    }

    /**
     * 判断一个 AttributeModifier 是否由 Baikiruto 创建。
     * 通过检查 modifier 的 key（1.21+ NamespacedKey）或 name 前缀来识别。
     */
    private fun isBaikirutoModifier(modifier: Any): Boolean {
        // 1.21+: 检查 NamespacedKey
        val getKey = modifier.javaClass.methods.firstOrNull { method ->
            method.name == "getKey" && method.parameterCount == 0
        }
        if (getKey != null) {
            val key = runCatching { getKey.invoke(modifier) }.getOrNull()
            val keyStr = key?.toString().orEmpty()
            if (keyStr.startsWith("baikiruto:")) {
                return true
            }
        }
        // Legacy: 检查 name 前缀
        val getName = modifier.javaClass.methods.firstOrNull { method ->
            method.name == "getName" && method.parameterCount == 0
        }
        if (getName != null) {
            val name = runCatching { getName.invoke(modifier) }.getOrNull()?.toString().orEmpty()
            if (name.startsWith("baikiruto.")) {
                return true
            }
        }
        return false
    }

    /**
     * 构建确定性的 modifier key，基于 attribute 名和 slot。
     * 确保同一 attribute+slot 组合始终使用相同的 key，避免重复叠加。
     */
    private fun buildDeterministicModifierKey(attributeName: String, slotName: String?): String {
        val normalized = attributeName.lowercase(Locale.ENGLISH)
            .removePrefix("generic_")
        return if (slotName != null) {
            "baikiruto.${normalized}.${slotName.lowercase(Locale.ENGLISH)}"
        } else {
            "baikiruto.${normalized}"
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

    /**
     * 解析 Attribute 常量，兼容 1.12-1.20（enum GENERIC_MAX_HEALTH）和 1.21+（Registry MAX_HEALTH）。
     *
     * 查找顺序：
     * 1. 原始名称（如 GENERIC_MAX_HEALTH）
     * 2. 去掉 GENERIC_ 前缀（如 MAX_HEALTH）
     * 3. 添加 GENERIC_ 前缀（如果原始名称没有）
     */
    private fun resolveAttributeConstant(attributeClass: Class<*>, name: String): Any? {
        debugLog("[Baikiruto/Debug]   resolveAttributeConstant: trying '$name'")
        resolveEnumConstant(attributeClass, name)?.let {
            debugLog("[Baikiruto/Debug]   resolveAttributeConstant: found '$name' directly")
            return it
        }

        // 1.21+: GENERIC_MAX_HEALTH -> MAX_HEALTH
        if (name.startsWith("GENERIC_")) {
            val withoutPrefix = name.removePrefix("GENERIC_")
            debugLog("[Baikiruto/Debug]   resolveAttributeConstant: trying without GENERIC_ prefix -> '$withoutPrefix'")
            resolveEnumConstant(attributeClass, withoutPrefix)?.let {
                debugLog("[Baikiruto/Debug]   resolveAttributeConstant: found '$withoutPrefix'")
                return it
            }
        }

        // 反向兼容: MAX_HEALTH -> GENERIC_MAX_HEALTH
        if (!name.startsWith("GENERIC_")) {
            val withPrefix = "GENERIC_$name"
            debugLog("[Baikiruto/Debug]   resolveAttributeConstant: trying with GENERIC_ prefix -> '$withPrefix'")
            resolveEnumConstant(attributeClass, withPrefix)?.let {
                debugLog("[Baikiruto/Debug]   resolveAttributeConstant: found '$withPrefix'")
                return it
            }
        }

        debugLog("[Baikiruto/Debug]   resolveAttributeConstant: all attempts failed for '$name'")
        return null
    }

    private fun resolveEnumConstant(enumClass: Class<*>, name: String): Any? {
        // 1) 标准 Java enum 查找
        enumClass.enumConstants?.firstOrNull { constant ->
            (constant as? Enum<*>)?.name == name
        }?.let { return it }

        // 2) 1.21+ Attribute 变为接口，通过 Registry 查找
        //    尝试 Attribute.valueOf(name) 静态方法（OldEnum 兼容）
        val valueOf = enumClass.methods.firstOrNull { method ->
            method.name == "valueOf" && method.parameterCount == 1 && method.parameterTypes[0] == String::class.java
        }
        if (valueOf != null) {
            val result = runCatching { invokeStaticWithReflex(enumClass, valueOf, name) }.getOrNull()
            if (result != null) return result
        }

        // 3) 通过 Registry.get(NamespacedKey) 查找
        val registryResult = resolveViaRegistry(enumClass, name)
        if (registryResult != null) return registryResult

        return null
    }

    /**
     * 通过 Bukkit Registry 查找常量。
     * 支持 1.21+ 中 Attribute 等从 enum 迁移到 Registry 的类型。
     */
    private fun resolveViaRegistry(targetClass: Class<*>, name: String): Any? {
        val registryClass = resolveClass("org.bukkit.Registry") ?: return null

        // 查找 Registry 上与目标类型匹配的静态字段
        val registryField = registryClass.fields.firstOrNull { field ->
            java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                runCatching {
                    val genericType = field.genericType
                    if (genericType is java.lang.reflect.ParameterizedType) {
                        genericType.actualTypeArguments.any { arg ->
                            arg == targetClass || (arg is Class<*> && targetClass.isAssignableFrom(arg))
                        }
                    } else {
                        false
                    }
                }.getOrDefault(false)
        }

        // 如果找不到泛型匹配，尝试按名称匹配（如 Registry.ATTRIBUTE）
        val registry = if (registryField != null) {
            runCatching { registryField.get(null) }.getOrNull()
        } else {
            val fieldName = targetClass.simpleName.uppercase(Locale.ENGLISH)
            runCatching { registryClass.getField(fieldName).get(null) }.getOrNull()
        } ?: return null

        // 尝试 registry.get(NamespacedKey)
        val namespacedKeyName = name.lowercase(Locale.ENGLISH)
        val namespacedKey = createNamespacedKey("minecraft:$namespacedKeyName") ?: return null
        val getMethod = registry.javaClass.methods.firstOrNull { method ->
            method.name == "get" && method.parameterCount == 1
        } ?: return null
        return invokeWithReflex(registry, getMethod, namespacedKey)
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
