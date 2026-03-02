package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemUpdater
import org.tabooproject.baikiruto.core.item.event.ItemCheckUpdateEvent
import org.tabooproject.baikiruto.core.item.event.ItemUpdateEvent
import taboolib.platform.util.isAir

object DefaultItemUpdater : ItemUpdater {

    override fun checkUpdate(player: Player?, inventory: Inventory): Int {
        var updated = 0
        for (slot in 0 until inventory.size) {
            val source = inventory.getItem(slot) ?: continue
            val (changed, rebuilt) = checkUpdateInternal(player, source)
            if (!changed) {
                continue
            }
            inventory.setItem(slot, rebuilt)
            updated += 1
        }
        if (player != null && updated > 0) {
            player.updateInventory()
        }
        return updated
    }

    override fun checkUpdate(player: Player?, itemStack: ItemStack): ItemStack {
        return checkUpdateInternal(player, itemStack).second
    }

    private fun checkUpdateInternal(player: Player?, itemStack: ItemStack): Pair<Boolean, ItemStack> {
        if (itemStack.isAir()) {
            return false to itemStack
        }
        val stream = Baikiruto.api().readItem(itemStack) ?: return false to itemStack
        val item = Baikiruto.api().getItem(stream.itemId) ?: return false to itemStack
        val latestHash = currentVersionHash(item)
        val lockedDisplayOutdated = isLockedDisplayOutdated(stream, item)
        val checkEvent = ItemCheckUpdateEvent(
            stream = stream,
            player = player,
            oldVersionHash = stream.versionHash,
            latestVersionHash = latestHash,
            isOutdated = latestHash != stream.versionHash || lockedDisplayOutdated
        )
        Baikiruto.api().getItemEventBus().post(checkEvent)
        if (checkEvent.cancelled) {
            return false to itemStack
        }
        val rebuiltStream = rebuildStreamForUpdate(stream, item, player)
        rebuiltStream.markSignal(ItemSignal.UPDATE_CHECKED)
        Baikiruto.api().getItemEventBus().post(
            ItemUpdateEvent(rebuiltStream, player, stream.versionHash, latestHash)
        )
        val rebuiltStack = rebuiltStream.toItemStack().apply {
            amount = itemStack.amount.coerceAtLeast(1)
        }
        return true to rebuiltStack
    }

    private fun currentVersionHash(item: Item): String {
        return if (item is DefaultItem) {
            item.latestVersionHash()
        } else {
            item.build().versionHash
        }
    }

    private fun isLockedDisplayOutdated(stream: ItemStream, item: Item): Boolean {
        val latestSignature = when (item) {
            is DefaultItem -> item.lockedDisplaySignature()
            else -> null
        }
        val currentSignature = LockedDisplaySignature.read(stream.runtimeData)
        return latestSignature != currentSignature
    }

    private fun rebuildStreamForUpdate(stream: ItemStream, item: Item, player: Player?): ItemStream {
        if (stream is DefaultItemStream) {
            return stream.rebuild(player)
        }
        val rebuiltStream = item.build(
            linkedMapOf<String, Any?>(
                "player" to player,
                "sender" to player,
                "ctx" to stream.runtimeData
            )
        )
        stream.runtimeData.forEach { (key, value) ->
            rebuiltStream.setRuntimeData(key, value)
        }
        return rebuiltStream
    }
}
