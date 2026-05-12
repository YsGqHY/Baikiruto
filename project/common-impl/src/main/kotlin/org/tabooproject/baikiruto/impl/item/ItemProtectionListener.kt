package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.ItemSpawnEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.item.feature.ItemDropEntityFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemProtectionFeature
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent

object ItemProtectionListener {

    private val placeActions = setOf("PLACE_ALL", "PLACE_ONE", "PLACE_SOME", "SWAP_WITH_CURSOR")
    private val hotbarActions = setOf("HOTBAR_SWAP", "HOTBAR_MOVE_AND_READD")

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemSpawn(event: ItemSpawnEvent) {
        val stream = read(event.entity.itemStack) ?: return
        ItemDropEntityFeature.apply(event.entity, stream)
    }

    @SubscribeEvent
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        if (event.inventory.matrix.any(::blocksCrafting)) {
            event.inventory.result = null
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.inventory.matrix.any(::blocksCrafting) ||
            blocksCrafting(event.currentItem) ||
            blocksCrafting(event.cursor)
        ) {
            cancel(event, event.whoClicked as? Player)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player
        val topInventory = event.view.topInventory
        val topName = inventoryTypeName(topInventory)
        if (isResultSlot(event) && topContainsBlockedStationItem(topInventory, topName)) {
            cancel(event, player)
            return
        }
        if (itemsEnteringTop(event, topInventory, player).any { stack -> blocksTopTarget(stack, topName) }) {
            cancel(event, player)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val topInventory = event.view.topInventory
        if (event.rawSlots.none { slot -> isTopSlot(slot, topInventory) }) {
            return
        }
        val topName = inventoryTypeName(topInventory)
        if (blocksTopTarget(event.oldCursor, topName)) {
            cancel(event, event.whoClicked as? Player)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryMoveItem(event: InventoryMoveItemEvent) {
        val target = inventoryTypeName(event.destination)
        if (blocksTopTarget(event.item, target)) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryPickupItem(event: InventoryPickupItemEvent) {
        val target = inventoryTypeName(event.inventory)
        if (blocksTopTarget(event.item.itemStack, target)) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        val stream = read(event.playerItem) ?: return
        if (ItemProtectionFeature.blocksContainer(stream, "ARMOR_STAND")) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPrepareEnchant(event: PrepareItemEnchantEvent) {
        val stream = read(event.item) ?: return
        if (ItemProtectionFeature.blocksStation(stream, "ENCHANTING")) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEnchant(event: EnchantItemEvent) {
        val stream = read(event.item) ?: return
        if (ItemProtectionFeature.blocksStation(stream, "ENCHANTING")) {
            event.isCancelled = true
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDropDamage(event: EntityDamageEvent) {
        val entity = event.entity as? Item ?: return
        val stream = read(entity.itemStack) ?: return
        if (!ItemProtectionFeature.isDestroyProtected(stream, event.cause)) {
            return
        }
        event.isCancelled = true
        if (ItemProtectionFeature.isFireCause(event.cause)) {
            entity.fireTicks = 0
        }
    }

    private fun itemsEnteringTop(
        event: InventoryClickEvent,
        topInventory: Inventory,
        player: Player?
    ): List<ItemStack?> {
        val topSlot = isTopSlot(event.rawSlot, topInventory)
        val action = event.action.name
        val items = arrayListOf<ItemStack?>()
        if (topSlot && action in placeActions) {
            items += event.cursor
        }
        if (topSlot && action in hotbarActions) {
            items += hotbarItem(player, event.hotbarButton)
        }
        if (!topSlot && action == "MOVE_TO_OTHER_INVENTORY") {
            items += event.currentItem
        }
        return items
    }

    private fun isResultSlot(event: InventoryClickEvent): Boolean {
        return event.slotType.name == "RESULT"
    }

    private fun topContainsBlockedStationItem(inventory: Inventory, station: String): Boolean {
        return inventory.contents.any { stack ->
            val stream = read(stack) ?: return@any false
            ItemProtectionFeature.blocksAnyCrafting(stream) || ItemProtectionFeature.blocksStation(stream, station)
        }
    }

    private fun blocksCrafting(itemStack: ItemStack?): Boolean {
        val stream = read(itemStack) ?: return false
        return ItemProtectionFeature.blocksVanillaCrafting(stream) || ItemProtectionFeature.blocksAnyCrafting(stream)
    }

    private fun blocksTopTarget(itemStack: ItemStack?, target: String): Boolean {
        val stream = read(itemStack) ?: return false
        return ItemProtectionFeature.blocksStation(stream, target) || ItemProtectionFeature.blocksContainer(stream, target)
    }

    private fun hotbarItem(player: Player?, hotbarButton: Int): ItemStack? {
        if (player == null || hotbarButton !in 0..8) {
            return null
        }
        return player.inventory.getItem(hotbarButton)
    }

    private fun inventoryTypeName(inventory: Inventory): String {
        return inventory.type.name
    }

    private fun isTopSlot(rawSlot: Int, topInventory: Inventory): Boolean {
        return rawSlot in 0 until topInventory.size
    }

    private fun read(itemStack: ItemStack?): ItemStream? {
        if (itemStack == null || itemStack.type == Material.AIR || itemStack.amount <= 0) {
            return null
        }
        return Baikiruto.apiOrNull()?.readItem(itemStack)
    }

    private fun cancel(event: Cancellable, player: Player?) {
        event.isCancelled = true
        player?.updateInventory()
    }
}
