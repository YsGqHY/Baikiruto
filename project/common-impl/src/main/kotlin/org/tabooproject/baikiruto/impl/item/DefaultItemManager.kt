package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemGroup
import org.tabooproject.baikiruto.core.item.ItemManager
import org.tabooproject.baikiruto.core.item.ItemModel
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.Registry
import org.tabooproject.baikiruto.core.item.event.ItemGiveEvent
import org.tabooproject.baikiruto.impl.item.registry.ConcurrentRegistry

class DefaultItemManager : ItemManager {

    private val itemRegistry = ConcurrentRegistry<Item>()
    private val modelRegistry = ConcurrentRegistry<ItemModel>()
    private val displayRegistry = ConcurrentRegistry<ItemDisplay>()
    private val groupRegistry = ConcurrentRegistry<ItemGroup>()

    override fun getItemRegistry(): Registry<Item> {
        return itemRegistry
    }

    override fun getModelRegistry(): Registry<ItemModel> {
        return modelRegistry
    }

    override fun getDisplayRegistry(): Registry<ItemDisplay> {
        return displayRegistry
    }

    override fun getGroupRegistry(): Registry<ItemGroup> {
        return groupRegistry
    }

    override fun registerItem(item: Item): Item {
        return itemRegistry.register(item.id, item)
    }

    override fun registerModel(model: ItemModel): ItemModel {
        return modelRegistry.register(model.id, model)
    }

    override fun registerDisplay(display: ItemDisplay): ItemDisplay {
        return displayRegistry.register(display.id, display)
    }

    override fun registerGroup(group: ItemGroup): ItemGroup {
        return groupRegistry.register(group.id, group)
    }

    override fun getItem(itemId: String): Item? {
        return itemRegistry.get(itemId)
    }

    override fun getModel(modelId: String): ItemModel? {
        return modelRegistry.get(modelId)
    }

    override fun getDisplay(displayId: String): ItemDisplay? {
        return displayRegistry.get(displayId)
    }

    override fun getGroup(groupId: String): ItemGroup? {
        return groupRegistry.get(groupId)
    }

    override fun generateItem(itemId: String, context: Map<String, Any?>): ItemStream? {
        return getItem(itemId)?.build(context)
    }

    override fun generateItemStack(itemId: String, context: Map<String, Any?>): ItemStack? {
        return generateItem(itemId, context)?.toItemStack()
    }

    override fun giveItem(player: Player, itemId: String, amount: Int, context: Map<String, Any?>): Boolean {
        val stream = generateItem(
            itemId = itemId,
            context = linkedMapOf<String, Any?>(
                "player" to player,
                "sender" to player
            ).apply { putAll(context) }
        ) ?: return false
        val event = ItemGiveEvent(stream, player, amount.coerceAtLeast(1))
        Baikiruto.api().getItemEventBus().post(event)
        if (event.cancelled) {
            return false
        }
        val itemStack = event.stream.toItemStack().apply {
            this.amount = event.amount.coerceAtLeast(1)
        }
        val overflow = player.inventory.addItem(itemStack)
        if (overflow.isNotEmpty()) {
            overflow.values.forEach { drop -> player.world.dropItemNaturally(player.location, drop) }
        }
        return true
    }
}
