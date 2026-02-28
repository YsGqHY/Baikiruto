package org.tabooproject.baikiruto.core.item

import org.bukkit.inventory.ItemStack

interface ItemHandler {

    fun read(itemStack: ItemStack): ItemStream?

    fun getItem(itemStack: ItemStack): Item?

    fun getItemId(itemStack: ItemStack): String?

    fun getItemData(itemStack: ItemStack): Map<String, Any?>?

    fun getItemUniqueData(itemStack: ItemStack): Map<String, Any?>?
}
