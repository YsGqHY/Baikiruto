package org.tabooproject.baikiruto.impl.script.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.item.feature.ItemCooldownFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemDurabilityFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemUniqueFeature

class ItemScriptOps(
    private val stream: ItemStream,
    private val player: Player?
) {

    fun data(key: String): Any? {
        return stream.getRuntimeData(key)
    }

    fun setData(key: String, value: Any?): ItemScriptOps {
        stream.setRuntimeData(key, value)
        return this
    }

    fun damage(amount: Int): Boolean {
        return ItemDurabilityFeature.applyDamage(stream, amount).destroyed
    }

    fun durability(): Int {
        return ItemDurabilityFeature.current(stream)
    }

    fun durabilityMax(): Int {
        return ItemDurabilityFeature.max(stream)
    }

    fun setDurability(value: Int): ItemScriptOps {
        ItemDurabilityFeature.setCurrent(stream, value)
        return this
    }

    fun cooldown(): Long {
        return ItemCooldownFeature.remainingTicks(stream, player)
    }

    fun setCooldown(ticks: Long): ItemScriptOps {
        ItemCooldownFeature.setRemainingTicks(stream, player, ticks)
        return this
    }

    fun owner(): String? {
        return ItemUniqueFeature.owner(stream)
    }

    fun isOwner(): Boolean {
        return ItemUniqueFeature.checkOwnership(stream, player).allowed
    }

    fun bindOwner(name: String? = null): Boolean {
        val target = name?.takeIf { it.isNotBlank() } ?: player?.name ?: return false
        return ItemUniqueFeature.bind(stream, target)
    }

    fun signal(name: String): ItemScriptOps {
        parseSignal(name)?.let(stream::markSignal)
        return this
    }

    fun hasSignal(name: String): Boolean {
        return parseSignal(name)?.let(stream::hasSignal) ?: false
    }

    fun rebuild(): ItemStack {
        return stream.toItemStack()
    }

    private fun parseSignal(name: String): ItemSignal? {
        val normalized = name.trim().uppercase().replace('-', '_')
        return ItemSignal.values().firstOrNull { it.name == normalized }
    }
}
