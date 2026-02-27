package org.tabooproject.baikiruto.core.item

import org.bukkit.inventory.ItemStack

interface ItemSerializer {

    fun serialize(itemStack: ItemStack): SerializedItem

    fun serialize(itemStream: ItemStream): SerializedItem

    fun deserialize(serializedItem: SerializedItem): ItemStream
}
