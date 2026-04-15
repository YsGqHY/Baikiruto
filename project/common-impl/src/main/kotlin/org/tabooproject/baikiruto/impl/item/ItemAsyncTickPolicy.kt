package org.tabooproject.baikiruto.impl.item

import java.util.Locale

object ItemAsyncTickPolicy {

    const val KEY_ENABLED = "async-tick-enabled"
    const val KEY_INTERVAL = "async-tick-interval"
    const val KEY_CONDITION_SNEAKING = "async-tick-condition-sneaking"
    const val KEY_CONDITION_SPRINTING = "async-tick-condition-sprinting"
    const val KEY_CONDITION_SWIMMING = "async-tick-condition-swimming"
    const val KEY_CONDITION_GLIDING = "async-tick-condition-gliding"
    const val KEY_CONDITION_FLYING = "async-tick-condition-flying"
    const val KEY_CONDITION_ON_GROUND = "async-tick-condition-on-ground"
    const val KEY_CONDITION_IN_VEHICLE = "async-tick-condition-in-vehicle"
    const val KEY_CONDITION_BURNING = "async-tick-condition-burning"
    const val KEY_CONDITION_BLOCKING = "async-tick-condition-blocking"
    const val KEY_CONDITION_SLOTS = "async-tick-condition-slots"
    const val KEY_CONDITION_WORLDS = "async-tick-condition-worlds"
    const val KEY_CONDITION_GAME_MODES = "async-tick-condition-game-modes"
    const val KEY_CONDITION_PERMISSIONS = "async-tick-condition-permissions"

    data class ConditionState(
        val slot: String,
        val sneaking: Boolean = false,
        val sprinting: Boolean = false,
        val swimming: Boolean = false,
        val gliding: Boolean = false,
        val flying: Boolean = false,
        val onGround: Boolean = false,
        val inVehicle: Boolean = false,
        val burning: Boolean = false,
        val blocking: Boolean = false,
        val world: String? = null,
        val gameMode: String? = null,
        val hasPermission: (String) -> Boolean = { false }
    )

    fun resolveEnabled(runtimeValue: Any?): Boolean {
        return when (runtimeValue) {
            null -> true
            is Boolean -> runtimeValue
            is Number -> runtimeValue.toInt() != 0
            is String -> runtimeValue.trim().equals("true", true) || runtimeValue.trim() == "1"
            else -> true
        }
    }

    fun resolveInterval(defaultInterval: Long, runtimeValue: Any?): Long {
        val fallback = defaultInterval.coerceAtLeast(1L)
        val resolved = when (runtimeValue) {
            is Number -> runtimeValue.toLong()
            is String -> runtimeValue.trim().toLongOrNull()
            else -> null
        } ?: return fallback
        return if (resolved >= 1L) resolved else fallback
    }

    fun resolveConditionSneaking(runtimeValue: Any?): Boolean? {
        return resolveConditionBoolean(runtimeValue)
    }

    fun resolveConditionBoolean(runtimeValue: Any?): Boolean? {
        return when (runtimeValue) {
            null -> null
            is Boolean -> runtimeValue
            is Number -> runtimeValue.toInt() != 0
            is String -> when (runtimeValue.trim().lowercase(Locale.ENGLISH)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }

    fun resolveConditionSlots(runtimeValue: Any?): Set<String> {
        return resolveConditionStrings(runtimeValue, ::normalizeSlot)
    }

    fun resolveConditionWorlds(runtimeValue: Any?): Set<String> {
        return resolveConditionStrings(runtimeValue, ::normalizeWorld)
    }

    fun resolveConditionGameModes(runtimeValue: Any?): Set<String> {
        return resolveConditionStrings(runtimeValue, ::normalizeGameMode)
    }

    fun resolveConditionPermissions(runtimeValue: Any?): Set<String> {
        return resolveConditionStrings(runtimeValue, ::normalizePermission)
    }

    fun normalizeSlot(raw: String): String? {
        return when (raw.trim().lowercase(Locale.ENGLISH).replace('-', '_')) {
            "mainhand", "main_hand", "hand" -> "MAINHAND"
            "offhand", "off_hand", "offhand_item" -> "OFFHAND"
            "head", "helmet" -> "HEAD"
            "chest", "chestplate", "body" -> "CHEST"
            "legs", "leggings" -> "LEGS"
            "feet", "boots" -> "FEET"
            "hotbar" -> "HOTBAR"
            "inventory", "storage", "main_inventory", "backpack" -> "INVENTORY"
            "armor", "armour" -> "ARMOR"
            "equipped", "equipment" -> "EQUIPPED"
            "all", "any" -> "ALL"
            else -> raw.trim().takeIf { it.isNotEmpty() }?.uppercase(Locale.ENGLISH)
        }
    }

    fun normalizeWorld(raw: String): String? {
        return raw.trim()
            .takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ENGLISH)
    }

    fun normalizeGameMode(raw: String): String? {
        return raw.trim()
            .takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.ENGLISH)
            ?.replace('-', '_')
    }

    fun normalizePermission(raw: String): String? {
        return raw.trim().takeIf { it.isNotEmpty() }
    }

    fun matchesConditions(
        conditionSneaking: Any?,
        conditionSlots: Any?,
        sneaking: Boolean,
        slot: String
    ): Boolean {
        return matchesConditions(
            conditions = mapOf(
                KEY_CONDITION_SNEAKING to conditionSneaking,
                KEY_CONDITION_SLOTS to conditionSlots
            ),
            state = ConditionState(
                slot = slot,
                sneaking = sneaking
            )
        )
    }

    fun matchesConditions(
        conditions: Map<String, Any?>,
        state: ConditionState
    ): Boolean {
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_SNEAKING], state.sneaking)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_SPRINTING], state.sprinting)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_SWIMMING], state.swimming)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_GLIDING], state.gliding)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_FLYING], state.flying)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_ON_GROUND], state.onGround)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_IN_VEHICLE], state.inVehicle)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_BURNING], state.burning)) return false
        if (!matchesBooleanCondition(conditions[KEY_CONDITION_BLOCKING], state.blocking)) return false
        if (!matchesSlotCondition(conditions[KEY_CONDITION_SLOTS], state.slot)) return false
        if (!matchesStringCondition(resolveConditionWorlds(conditions[KEY_CONDITION_WORLDS]), state.world, ::normalizeWorld)) return false
        if (!matchesStringCondition(resolveConditionGameModes(conditions[KEY_CONDITION_GAME_MODES]), state.gameMode, ::normalizeGameMode)) return false
        if (!matchesPermissionCondition(resolveConditionPermissions(conditions[KEY_CONDITION_PERMISSIONS]), state.hasPermission)) return false
        return true
    }

    fun stableSeed(playerKey: String, slotIndex: Int, itemId: String): Int {
        var result = playerKey.hashCode()
        result = 31 * result + slotIndex
        result = 31 * result + itemId.hashCode()
        return result
    }

    fun shouldTrigger(currentTick: Long, interval: Long, seed: Int): Boolean {
        val normalizedInterval = interval.coerceAtLeast(1L)
        if (normalizedInterval == 1L) {
            return true
        }
        val offset = Math.floorMod(seed.toLong(), normalizedInterval)
        return Math.floorMod(currentTick + offset, normalizedInterval) == 0L
    }

    private fun matchesBooleanCondition(runtimeValue: Any?, actual: Boolean): Boolean {
        val required = resolveConditionBoolean(runtimeValue) ?: return true
        return required == actual
    }

    private fun matchesSlotCondition(runtimeValue: Any?, slot: String): Boolean {
        val requiredSlots = resolveConditionSlots(runtimeValue)
        if (requiredSlots.isEmpty()) {
            return true
        }
        val currentSlot = normalizeSlot(slot) ?: return false
        if ("ALL" in requiredSlots) {
            return true
        }
        return requiredSlots.any { required ->
            when (required) {
                "HOTBAR" -> currentSlot == "HOTBAR" || currentSlot == "MAINHAND"
                "ARMOR" -> currentSlot == "HEAD" || currentSlot == "CHEST" || currentSlot == "LEGS" || currentSlot == "FEET"
                "EQUIPPED" -> currentSlot == "MAINHAND" || currentSlot == "OFFHAND" || currentSlot == "HEAD" || currentSlot == "CHEST" || currentSlot == "LEGS" || currentSlot == "FEET"
                else -> required == currentSlot
            }
        }
    }

    private fun matchesStringCondition(requiredValues: Set<String>, actualValue: String?, normalizer: (String) -> String?): Boolean {
        if (requiredValues.isEmpty()) {
            return true
        }
        val normalizedActual = actualValue?.let(normalizer) ?: return false
        return normalizedActual in requiredValues
    }

    private fun matchesPermissionCondition(requiredPermissions: Set<String>, hasPermission: (String) -> Boolean): Boolean {
        if (requiredPermissions.isEmpty()) {
            return true
        }
        return requiredPermissions.any { permission ->
            runCatching { hasPermission(permission) }.getOrDefault(false)
        }
    }

    private fun resolveConditionStrings(runtimeValue: Any?, normalizer: (String) -> String?): Set<String> {
        val values = linkedSetOf<String>()
        when (runtimeValue) {
            null -> Unit
            is String -> addConditionStrings(values, runtimeValue, normalizer)
            is Iterable<*> -> runtimeValue.forEach { entry ->
                when (entry) {
                    null -> Unit
                    is String -> addConditionStrings(values, entry, normalizer)
                    else -> normalizer(entry.toString())?.let(values::add)
                }
            }
            else -> normalizer(runtimeValue.toString())?.let(values::add)
        }
        return values
    }

    private fun addConditionStrings(values: MutableSet<String>, source: String, normalizer: (String) -> String?) {
        source.split(',', '\n')
            .mapNotNull(normalizer)
            .forEach(values::add)
    }
}
