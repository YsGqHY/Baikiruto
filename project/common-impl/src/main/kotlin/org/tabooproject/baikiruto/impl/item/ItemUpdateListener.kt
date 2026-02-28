package org.tabooproject.baikiruto.impl.item

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.common.platform.Schedule
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent

object ItemUpdateListener {

    @Schedule(period = 100)
    fun onTick() {
        Bukkit.getOnlinePlayers().forEach { player ->
            Baikiruto.api().getItemUpdater().checkUpdate(player, player.inventory)
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        val source = event.itemDrop.itemStack
        val updated = Baikiruto.api().getItemUpdater().checkUpdate(event.player, source)
        if (updated !== source) {
            event.itemDrop.itemStack = updated
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val source = event.item.itemStack
        val updated = Baikiruto.api().getItemUpdater().checkUpdate(player, source)
        if (updated !== source) {
            event.item.itemStack = updated
        }
    }

    @SubscribeEvent(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val inventory = event.inventory
        if (inventory.location != null) {
            Baikiruto.api().getItemUpdater().checkUpdate(player, inventory)
        }
    }

    @SubscribeEvent(bind = "cc.bukkitPlugin.pds.events.PlayerDataLoadCompleteEvent")
    fun onPdsLoad(event: OptionalEvent) {
        val player = event.read<Player>("player") ?: return
        Baikiruto.api().getItemUpdater().checkUpdate(player, player.inventory)
    }
}
