package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.tabooproject.baikiruto.core.item.ItemStream
import java.util.Locale

object ItemCombatFeature {

    private const val KEY_EQUIPPABLE_SLOT = "equippable-slot"
    private const val KEY_DAMAGE_RESISTANT_ENABLED = "damage-resistant-enabled"
    private const val KEY_DAMAGE_RESISTANT_TYPES = "damage-resistant-types"
    private const val KEY_DEATH_PROTECTION_ENABLED = "death-protection-enabled"
    private const val KEY_DEATH_PROTECTION_TYPES = "death-protection-types"
    private const val KEY_DEATH_PROTECTION_HEALTH = "death-protection-health"
    private const val KEY_DEATH_PROTECTION_CONSUME = "death-protection-consume"

    fun isDamageResistant(stream: ItemStream, slot: String, cause: EntityDamageEvent.DamageCause): Boolean {
        if (!matchesSlot(stream, slot)) {
            return false
        }
        if (asBoolean(stream.getRuntimeData(KEY_DAMAGE_RESISTANT_ENABLED)) != true) {
            return false
        }
        val types = parseTypeList(stream.getRuntimeData(KEY_DAMAGE_RESISTANT_TYPES))
        return matchesCause(types, cause)
    }

    fun canProtectDeath(stream: ItemStream, slot: String, cause: EntityDamageEvent.DamageCause): Boolean {
        if (!matchesSlot(stream, slot)) {
            return false
        }
        if (asBoolean(stream.getRuntimeData(KEY_DEATH_PROTECTION_ENABLED)) != true) {
            return false
        }
        val types = parseTypeList(stream.getRuntimeData(KEY_DEATH_PROTECTION_TYPES))
        return matchesCause(types, cause)
    }

    fun resolveProtectionHealth(stream: ItemStream, player: Player): Double {
        val configured = when (val value = stream.getRuntimeData(KEY_DEATH_PROTECTION_HEALTH)) {
            is Number -> value.toDouble()
            is String -> value.trim().toDoubleOrNull()
            else -> null
        } ?: 1.0
        return configured.coerceIn(0.5, player.maxHealth)
    }

    fun shouldConsumeProtection(stream: ItemStream): Boolean {
        return asBoolean(stream.getRuntimeData(KEY_DEATH_PROTECTION_CONSUME)) ?: true
    }

    private fun matchesSlot(stream: ItemStream, slot: String): Boolean {
        val required = stream.getRuntimeData(KEY_EQUIPPABLE_SLOT)?.toString()?.trim()?.uppercase(Locale.ENGLISH)
            ?: return true
        val current = slot.trim().uppercase(Locale.ENGLISH)
        return required == current
    }

    private fun matchesCause(types: List<String>, cause: EntityDamageEvent.DamageCause): Boolean {
        if (types.isEmpty()) {
            return true
        }
        val normalizedCause = cause.name.lowercase(Locale.ENGLISH)
        return types.any { type -> matchesDamageType(type, normalizedCause) }
    }

    private fun matchesDamageType(type: String, cause: String): Boolean {
        val normalized = type.trim().lowercase(Locale.ENGLISH).replace('-', '_').substringAfter(':')
        if (normalized == cause || normalized == "all") {
            return true
        }
        return when (normalized) {
            "fire" -> cause == "fire" || cause == "fire_tick" || cause == "lava" || cause == "hot_floor"
            "projectile" -> cause == "projectile"
            "explosion" -> cause == "entity_explosion" || cause == "block_explosion"
            "fall" -> cause == "fall" || cause == "fly_into_wall"
            "magic" -> cause == "magic" || cause == "poison" || cause == "wither"
            "out_of_world" -> cause == "void"
            else -> false
        }
    }

    private fun parseTypeList(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> listOf(source)
            is Iterable<*> -> source.mapNotNull { it?.toString() }
            else -> emptyList()
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun asBoolean(source: Any?): Boolean? {
        return when (source) {
            null -> null
            is Boolean -> source
            is Number -> source.toInt() != 0
            is String -> when (source.trim().lowercase(Locale.ENGLISH)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }
}
