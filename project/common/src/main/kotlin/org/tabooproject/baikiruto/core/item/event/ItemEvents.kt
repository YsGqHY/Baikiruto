package org.tabooproject.baikiruto.core.item.event

import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.item.ItemStream

open class ItemLifecycleEvent(
    val stream: ItemStream,
    val player: Player?,
    val source: Any?,
    val context: MutableMap<String, Any?> = linkedMapOf()
) {

    var cancelled: Boolean = false

    val itemId: String
        get() = stream.itemId
}

class ItemBuildPreEvent(
    stream: ItemStream,
    player: Player?,
    context: MutableMap<String, Any?>
) : ItemLifecycleEvent(stream, player, null, context)

class ItemBuildPostEvent(
    stream: ItemStream,
    player: Player?,
    context: MutableMap<String, Any?>
) : ItemLifecycleEvent(stream, player, null, context)

class ItemReleaseEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemLifecycleEvent(stream, player, source, context)

class ItemReleaseDisplayEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemLifecycleEvent(stream, player, source, context)

class ItemReleaseFinalEvent(
    stream: ItemStream,
    player: Player?,
    source: Any?,
    context: MutableMap<String, Any?>
) : ItemLifecycleEvent(stream, player, source, context)

class ItemGiveEvent(
    stream: ItemStream,
    player: Player,
    var amount: Int
) : ItemLifecycleEvent(stream, player, null)

class ItemUpdateEvent(
    stream: ItemStream,
    player: Player?,
    val oldVersionHash: String,
    val newVersionHash: String
) : ItemLifecycleEvent(stream, player, null)
