package org.tabooproject.baikiruto.core.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface ItemManager {

    fun getItemRegistry(): Registry<Item>

    fun getModelRegistry(): Registry<ItemModel>

    fun getDisplayRegistry(): Registry<ItemDisplay>

    fun getGroupRegistry(): Registry<ItemGroup>

    fun registerItem(item: Item): Item

    fun registerModel(model: ItemModel): ItemModel

    fun registerDisplay(display: ItemDisplay): ItemDisplay

    fun registerGroup(group: ItemGroup): ItemGroup

    fun getItem(itemId: String): Item?

    fun getModel(modelId: String): ItemModel?

    fun getDisplay(displayId: String): ItemDisplay?

    fun getGroup(groupId: String): ItemGroup?

    fun generateItem(itemId: String, context: Map<String, Any?> = emptyMap()): ItemStream?

    fun generateItemStack(itemId: String, context: Map<String, Any?> = emptyMap()): ItemStack?

    fun giveItem(player: Player, itemId: String, amount: Int = 1, context: Map<String, Any?> = emptyMap()): Boolean
}
