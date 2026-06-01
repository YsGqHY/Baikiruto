package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

fun interface ItemUpdateStatePreserver {

    fun preserve(source: ItemStack, rebuilt: ItemStack, player: Player?): ItemStack
}
