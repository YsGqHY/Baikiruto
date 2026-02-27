package org.tabooproject.baikiruto.core

import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemGroup
import org.tabooproject.baikiruto.core.item.ItemManager
import org.tabooproject.baikiruto.core.item.ItemModel
import org.tabooproject.baikiruto.core.item.ItemSerializer
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemUpdater
import org.tabooproject.baikiruto.core.item.Registry
import org.tabooproject.baikiruto.core.item.event.ItemEventBus

/**
 * Baikiruto
 * org.tabooproject.baikiruto.core.BaikirutoAPI
 *
 * @author mical
 * @since 2026/2/26 23:03
 */
interface BaikirutoAPI {

    fun getScriptHandler(): BaikirutoScriptHandler

    fun getItemManager(): ItemManager

    fun getItemRegistry(): Registry<Item>

    fun getModelRegistry(): Registry<ItemModel>

    fun getDisplayRegistry(): Registry<ItemDisplay>

    fun getGroupRegistry(): Registry<ItemGroup>

    fun registerItem(item: Item): Item

    fun getItem(itemId: String): Item?

    fun getModel(modelId: String): ItemModel? {
        return getItemManager().getModel(modelId)
    }

    fun getDisplay(displayId: String): ItemDisplay? {
        return getItemManager().getDisplay(displayId)
    }

    fun getGroup(groupId: String): ItemGroup? {
        return getItemManager().getGroup(groupId)
    }

    fun buildItem(itemId: String, context: Map<String, Any?> = emptyMap()): ItemStack?

    fun readItem(itemStack: ItemStack): ItemStream?

    fun getItemSerializer(): ItemSerializer

    fun getItemUpdater(): ItemUpdater

    fun getItemEventBus(): ItemEventBus

    fun getItemId(itemStack: ItemStack): String? {
        return readItem(itemStack)?.itemId
    }
}
