package org.tabooproject.baikiruto.core.item

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

interface ItemUpdater {

    fun checkUpdate(player: Player?, inventory: Inventory): Int

    fun checkUpdate(player: Player?, itemStack: ItemStack): ItemStack
}
