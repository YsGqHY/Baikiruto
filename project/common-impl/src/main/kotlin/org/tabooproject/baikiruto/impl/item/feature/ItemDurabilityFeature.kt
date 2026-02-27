package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.item.DefaultItemStream
import kotlin.math.roundToInt

object ItemDurabilityFeature {

    private const val KEY_MAX = "durability"
    private const val KEY_CURRENT = "durability_current"
    private const val KEY_REMAINS = "durability-remains"
    private const val KEY_SYNC = "durability-synchronous"
    private const val KEY_BAR_LENGTH = "durability-bar-length"
    private const val KEY_BAR_SYMBOL = "durability-bar-symbol"

    data class DamageResult(
        val applied: Boolean,
        val destroyed: Boolean
    )

    fun max(stream: ItemStream): Int {
        return intValue(stream.getRuntimeData(KEY_MAX)) ?: -1
    }

    fun current(stream: ItemStream): Int {
        val max = max(stream)
        if (max <= 0) {
            return -1
        }
        return (intValue(stream.getRuntimeData(KEY_CURRENT)) ?: max).coerceIn(0, max)
    }

    fun setCurrent(stream: ItemStream, value: Int): Boolean {
        val max = max(stream)
        if (max <= 0) {
            return false
        }
        val next = value.coerceIn(0, max)
        stream.setRuntimeData(KEY_CURRENT, next)
        stream.markSignal(if (next <= 0) ItemSignal.DURABILITY_DESTROYED else ItemSignal.DURABILITY_CHANGED)
        return true
    }

    fun prepare(stream: DefaultItemStream) {
        val max = intValue(stream.getRuntimeData(KEY_MAX)) ?: return
        if (max <= 0) {
            return
        }
        val currentRaw = intValue(stream.getRuntimeData(KEY_CURRENT))
        val current = (currentRaw ?: max).coerceIn(0, max)
        stream.setRuntimeData(KEY_CURRENT, current)
        stream.setRuntimeData("durability_max", max)
        stream.setRuntimeData("durability_percent", current.toDouble() / max.toDouble())
        stream.setRuntimeData("durability_bar", buildDurabilityBar(stream, current, max))

        if (booleanValue(stream.getRuntimeData(KEY_SYNC)) == true) {
            syncVanillaDamage(stream, current, max)
        }
    }

    fun applyDamage(stream: ItemStream, damage: Int): DamageResult {
        val max = intValue(stream.getRuntimeData(KEY_MAX)) ?: return DamageResult(applied = false, destroyed = false)
        if (max <= 0) {
            return DamageResult(applied = false, destroyed = false)
        }
        val safeDamage = damage.coerceAtLeast(0)
        if (safeDamage <= 0) {
            return DamageResult(applied = true, destroyed = false)
        }
        val currentRaw = intValue(stream.getRuntimeData(KEY_CURRENT))
        val current = (currentRaw ?: max).coerceIn(0, max)
        val next = (current - safeDamage).coerceAtLeast(0)
        stream.setRuntimeData(KEY_CURRENT, next)
        stream.markSignal(if (next <= 0) ItemSignal.DURABILITY_DESTROYED else ItemSignal.DURABILITY_CHANGED)
        return DamageResult(applied = true, destroyed = next <= 0)
    }

    fun resolveDestroyedItem(stream: ItemStream, player: Player?): ItemStack {
        val remainsItemId = stream.getRuntimeData(KEY_REMAINS)?.toString()?.trim()
        if (remainsItemId.isNullOrBlank()) {
            return ItemStack(Material.AIR)
        }
        return Baikiruto.api().buildItem(
            itemId = remainsItemId,
            context = linkedMapOf<String, Any?>(
                "player" to player,
                "sender" to player,
                "ctx" to mapOf(
                    "destroyed" to stream.itemId
                )
            )
        ) ?: ItemStack(Material.AIR)
    }

    private fun buildDurabilityBar(stream: ItemStream, current: Int, max: Int): String {
        val length = intValue(stream.getRuntimeData(KEY_BAR_LENGTH))?.coerceAtLeast(1) ?: 10
        val symbol = stream.getRuntimeData(KEY_BAR_SYMBOL)
        val symbols = when (symbol) {
            is Iterable<*> -> symbol.mapNotNull { it?.toString() }.takeIf { it.size >= 2 }
            is String -> symbol.split(',').map { it.trim() }.takeIf { it.size >= 2 }
            else -> null
        } ?: listOf("&a|", "&7|")
        val filled = ((current.toDouble() / max.toDouble()) * length.toDouble()).roundToInt().coerceIn(0, length)
        return buildString(length * 2) {
            repeat(filled) { append(symbols[0]) }
            repeat(length - filled) { append(symbols[1]) }
        }
    }

    private fun syncVanillaDamage(stream: DefaultItemStream, current: Int, max: Int) {
        val vanillaMax = stream.itemStack().type.maxDurability.toInt()
        if (vanillaMax <= 0) {
            return
        }
        val percent = current.toDouble() / max.toDouble()
        val damage = (vanillaMax.toDouble() * (1.0 - percent)).roundToInt().coerceIn(0, vanillaMax)
        stream.setRuntimeData("damage", damage)
    }

    private fun intValue(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
    }

    private fun booleanValue(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> when (value.trim().lowercase()) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }
}
