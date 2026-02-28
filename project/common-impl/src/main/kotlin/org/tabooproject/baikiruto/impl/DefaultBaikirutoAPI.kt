package org.tabooproject.baikiruto.impl

import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.BaikirutoAPI
import org.tabooproject.baikiruto.core.BaikirutoScriptHandler
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemGroup
import org.tabooproject.baikiruto.core.item.ItemHandler
import org.tabooproject.baikiruto.core.item.ItemLoader
import org.tabooproject.baikiruto.core.item.ItemManager
import org.tabooproject.baikiruto.core.item.ItemModel
import org.tabooproject.baikiruto.core.item.ItemSerializer
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemUpdater
import org.tabooproject.baikiruto.core.item.Registry
import org.tabooproject.baikiruto.core.item.event.ItemEventBus
import org.tabooproject.baikiruto.impl.item.DefaultItemHandler
import org.tabooproject.baikiruto.impl.item.DefaultItemLoader
import org.tabooproject.baikiruto.impl.item.DefaultItemManager
import org.tabooproject.baikiruto.impl.item.DefaultItemSerializer
import org.tabooproject.baikiruto.impl.item.DefaultItemUpdater
import org.tabooproject.baikiruto.impl.item.event.DefaultItemEventBus
import org.tabooproject.baikiruto.impl.log.BaikirutoLog
import taboolib.common.platform.PlatformFactory

/**
 * Baikiruto
 * org.tabooproject.baikiruto.impl.DefaultBaikirutoAPI
 *
 * @author mical
 * @since 2026/2/26 23:07
 */
class DefaultBaikirutoAPI : BaikirutoAPI {

    private val itemManager = DefaultItemManager()
    private val itemHandler = DefaultItemHandler(itemManager)
    private val itemLoader = DefaultItemLoader
    private val itemSerializer = DefaultItemSerializer
    private val itemUpdater = DefaultItemUpdater
    private val itemEventBus = DefaultItemEventBus

    override fun getScriptHandler(): BaikirutoScriptHandler {
        return try {
            PlatformFactory.getAPI<BaikirutoScriptHandler>()
        } catch (ex: Throwable) {
            BaikirutoLog.serviceMissing("BaikirutoScriptHandler", ex)
            throw ex
        }
    }

    override fun getItemManager(): ItemManager {
        return itemManager
    }

    override fun getItemHandler(): ItemHandler {
        return itemHandler
    }

    override fun getItemLoader(): ItemLoader {
        return itemLoader
    }

    override fun getItemRegistry(): Registry<Item> {
        return itemManager.getItemRegistry()
    }

    override fun getModelRegistry(): Registry<ItemModel> {
        return itemManager.getModelRegistry()
    }

    override fun getDisplayRegistry(): Registry<ItemDisplay> {
        return itemManager.getDisplayRegistry()
    }

    override fun getGroupRegistry(): Registry<ItemGroup> {
        return itemManager.getGroupRegistry()
    }

    override fun registerItem(item: Item): Item {
        return itemManager.registerItem(item)
    }

    override fun getItem(itemId: String): Item? {
        return itemManager.getItem(itemId)
    }

    override fun buildItem(itemId: String, context: Map<String, Any?>): ItemStack? {
        return itemManager.generateItemStack(itemId, context)
    }

    override fun readItem(itemStack: ItemStack): ItemStream? {
        return itemHandler.read(itemStack)
    }

    override fun getItemSerializer(): ItemSerializer {
        return itemSerializer
    }

    override fun getItemUpdater(): ItemUpdater {
        return itemUpdater
    }

    override fun getItemEventBus(): ItemEventBus {
        return itemEventBus
    }
}
