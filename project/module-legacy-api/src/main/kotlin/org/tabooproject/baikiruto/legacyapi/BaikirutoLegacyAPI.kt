package org.tabooproject.baikiruto.legacyapi

import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.SerializedItem
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

@Deprecated("Use org.tabooproject.baikiruto.core.Baikiruto#api()")
object BaikirutoLegacyAPI {

    fun getItem(id: String, context: Map<String, Any?> = emptyMap()): ItemStack? {
        return Baikiruto.api().buildItem(id, context)
    }

    fun getItemStack(id: String, context: Map<String, Any?> = emptyMap()): ItemStack? {
        return getItem(id, context)
    }

    fun read(item: ItemStack): ItemStream? {
        return Baikiruto.api().readItem(item)
    }

    fun getName(item: ItemStack): String? {
        return Baikiruto.api().getItemId(item)
    }

    fun getData(item: ItemStack): Map<String, Any?>? {
        return Baikiruto.api().getItemData(item)
    }

    fun getUnique(item: ItemStack): Map<String, Any?>? {
        return Baikiruto.api().getItemUniqueData(item)
    }

    fun checkUpdate(player: Player?, inventory: Inventory): Int {
        return Baikiruto.api().getItemUpdater().checkUpdate(player, inventory)
    }

    fun checkUpdate(player: Player?, item: ItemStack): ItemStack {
        return Baikiruto.api().getItemUpdater().checkUpdate(player, item)
    }

    fun reload() {
        Baikiruto.api().reload()
    }

    fun loadedIds(): Set<String> {
        return Baikiruto.api().getItemLoader().loadedIds()
    }

    fun serialize(item: ItemStack): SerializedItem {
        return Baikiruto.api().getItemSerializer().serialize(item)
    }

    fun serialize(stream: ItemStream): SerializedItem {
        return Baikiruto.api().getItemSerializer().serialize(stream)
    }

    fun deserialize(serializedItem: SerializedItem): ItemStream {
        return Baikiruto.api().getItemSerializer().deserialize(serializedItem)
    }
}
