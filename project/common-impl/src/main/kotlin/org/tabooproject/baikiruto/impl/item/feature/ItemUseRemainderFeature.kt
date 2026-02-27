package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemStream

object ItemUseRemainderFeature {

    private const val KEY_REMAINDER = "use-remainder"
    private const val KEY_AMOUNT = "use-remainder-amount"

    fun resolve(stream: ItemStream, player: Player?): ItemStack? {
        val raw = stream.getRuntimeData(KEY_REMAINDER) ?: return null
        val id = raw.toString().trim().takeIf { it.isNotEmpty() } ?: return null
        val amount = parseAmount(stream.getRuntimeData(KEY_AMOUNT))
        val context = linkedMapOf<String, Any?>(
            "player" to player,
            "sender" to player,
            "ctx" to mapOf(
                "source" to stream.itemId,
                "remainder" to id
            )
        )
        val managed = Baikiruto.api().buildItem(id, context)
        val stack = managed ?: createVanillaItem(id) ?: return null
        stack.amount = amount.coerceAtLeast(1)
        return stack
    }

    fun give(player: Player, itemStack: ItemStack) {
        if (itemStack.type == Material.AIR || itemStack.amount <= 0) {
            return
        }
        val overflow = player.inventory.addItem(itemStack)
        if (overflow.isNotEmpty()) {
            overflow.values.forEach { drop ->
                player.world.dropItemNaturally(player.location, drop)
            }
        }
    }

    private fun createVanillaItem(raw: String): ItemStack? {
        val material = Material.matchMaterial(raw)
            ?: Material.matchMaterial(raw.substringAfter(':'))
            ?: Material.matchMaterial(raw.substringAfter(':').uppercase())
            ?: return null
        return ItemStack(material)
    }

    private fun parseAmount(raw: Any?): Int {
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> 1
        }?.coerceAtLeast(1) ?: 1
    }
}
