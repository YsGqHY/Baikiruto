package org.tabooproject.baikiruto.impl.item

import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemHandler
import org.tabooproject.baikiruto.core.item.ItemManager
import org.tabooproject.baikiruto.core.item.ItemStream

class DefaultItemHandler(
    private val itemManager: ItemManager
) : ItemHandler {

    override fun read(itemStack: ItemStack): ItemStream? {
        val payload = ItemStreamTransport.read(itemStack) ?: return null
        return ItemStreamTransport.create(itemStack, payload)
    }

    override fun getItem(itemStack: ItemStack): Item? {
        val stream = read(itemStack) ?: return null
        return itemManager.getItem(stream.itemId)
    }

    override fun getItemId(itemStack: ItemStack): String? {
        return read(itemStack)?.itemId
    }

    override fun getItemData(itemStack: ItemStack): Map<String, Any?>? {
        return read(itemStack)?.runtimeData
    }

    override fun getItemUniqueData(itemStack: ItemStack): Map<String, Any?>? {
        val stream = read(itemStack) ?: return null
        val runtime = stream.runtimeData
        val unique = linkedMapOf<String, Any?>()
        runtime["unique.player"]?.let { unique["player"] = it }
        runtime["unique.date"]?.let { unique["date"] = it }
        runtime["unique.uuid"]?.let { unique["uuid"] = it }
        return unique.takeIf { it.isNotEmpty() }
    }
}
