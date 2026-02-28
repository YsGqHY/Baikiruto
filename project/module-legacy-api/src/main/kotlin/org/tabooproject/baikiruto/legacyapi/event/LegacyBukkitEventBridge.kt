package org.tabooproject.baikiruto.legacyapi.event

import ink.ptms.zaphkiel.api.event.ItemBuildEvent
import ink.ptms.zaphkiel.api.event.ItemGiveEvent
import ink.ptms.zaphkiel.api.event.ItemReleaseEvent
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.event.ItemBuildPostEvent
import org.tabooproject.baikiruto.core.item.event.ItemBuildPreEvent
import org.tabooproject.baikiruto.core.item.event.ItemCheckUpdateEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseDisplayBuildEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseEvent as CoreReleaseEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseFinalEvent
import org.tabooproject.baikiruto.core.item.event.ItemSelectDisplayEvent
import org.tabooproject.baikiruto.core.item.event.ItemGiveEvent as CoreGiveEvent
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import java.util.concurrent.atomic.AtomicBoolean

object LegacyBukkitEventBridge {

    private val initialized = AtomicBoolean(false)

    @Awake(LifeCycle.ACTIVE)
    private fun onActive() {
        if (!initialized.compareAndSet(false, true)) {
            return
        }
        val bus = Baikiruto.api().getItemEventBus()
        bus.subscribe(ItemBuildPreEvent::class.java) { event ->
            val proxy = ItemBuildEvent.Pre(event.player, event.stream, event.name, event.lore)
            proxy.isCancelled = event.cancelled
            runCatching { proxy.call() }
            event.cancelled = proxy.isCancelled
        }
        bus.subscribe(ItemBuildPostEvent::class.java) { event ->
            val proxy = ItemBuildEvent.Post(event.player, event.stream, event.name, event.lore)
            runCatching { proxy.call() }
        }
        bus.subscribe(ItemCheckUpdateEvent::class.java) { event ->
            val proxy = ItemBuildEvent.CheckUpdate(
                player = event.player,
                itemStream = event.stream,
                isOutdated = event.isOutdated
            )
            proxy.isCancelled = event.cancelled
            runCatching { proxy.call() }
            event.cancelled = proxy.isCancelled
            event.isOutdated = !proxy.isCancelled
        }
        bus.subscribe(CoreReleaseEvent::class.java) { event ->
            val itemMeta = event.itemMeta ?: return@subscribe
            val proxy = ItemReleaseEvent(
                icon = event.icon,
                data = event.data,
                itemMeta = itemMeta,
                itemStream = event.stream,
                player = event.player
            )
            runCatching { proxy.call() }
            event.icon = proxy.icon
            event.data = proxy.data
            event.itemMeta = proxy.itemMeta
        }
        bus.subscribe(ItemSelectDisplayEvent::class.java) { event ->
            val proxy = ItemReleaseEvent.SelectDisplay(
                itemStream = event.stream,
                display = event.display,
                player = event.player
            )
            runCatching { proxy.call() }
            event.display = proxy.display
            event.displayId = proxy.display?.id ?: event.displayId
        }
        bus.subscribe(ItemReleaseDisplayBuildEvent::class.java) { event ->
            val proxy = ItemReleaseEvent.Display(
                itemStream = event.stream,
                name = event.name,
                lore = event.lore,
                player = event.player
            )
            runCatching { proxy.call() }
        }
        bus.subscribe(ItemReleaseFinalEvent::class.java) { event ->
            val proxy = ItemReleaseEvent.Final(
                itemStack = event.itemStack,
                itemStream = event.stream,
                player = event.player
            )
            runCatching { proxy.call() }
            event.itemStack = proxy.itemStack
        }
        bus.subscribe(CoreGiveEvent::class.java) { event ->
            val proxy = ItemGiveEvent(
                player = event.player ?: return@subscribe,
                itemStream = event.stream,
                amount = event.amount
            )
            proxy.isCancelled = event.cancelled
            runCatching { proxy.call() }
            event.cancelled = proxy.isCancelled
            event.amount = proxy.amount
            event.stream = proxy.itemStream
        }
    }
}
