package org.tabooproject.baikiruto.impl.item.event

import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.bukkit.event.Cancellable
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemStreamData
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.event.ItemBuildPostEvent
import org.tabooproject.baikiruto.core.item.event.ItemBuildPreEvent
import org.tabooproject.baikiruto.core.item.event.ItemActionTriggerEvent
import org.tabooproject.baikiruto.core.item.event.ItemLifecycleEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseDisplayBuildEvent
import org.tabooproject.baikiruto.core.item.event.ItemSelectActionEvent
import org.tabooproject.baikiruto.core.item.event.ItemSelectDisplayEvent
import org.tabooproject.baikiruto.core.item.ItemDisplay

class ItemActionEventBusTest {

    @Test
    fun `should receive action trigger as lifecycle event`() {
        val captured = arrayListOf<ItemLifecycleEvent>()
        val subscription = DefaultItemEventBus.subscribe(ItemLifecycleEvent::class.java) { captured += it }
        try {
            val event = ItemActionTriggerEvent(
                stream = stubStream(),
                player = null,
                source = null,
                context = linkedMapOf("foo" to "bar"),
                trigger = ItemScriptTrigger.SELECT
            )
            DefaultItemEventBus.post(event)
            assertEquals(1, captured.size)
            assertSame(event, captured.first())
        } finally {
            subscription.close()
        }
    }

    @Test
    fun `should support save cancel and context mutation`() {
        val subscription = DefaultItemEventBus.subscribe(ItemActionTriggerEvent::class.java) { event ->
            event.save = true
            event.cancelled = true
            event.context["injected"] = 1
        }
        try {
            val event = ItemActionTriggerEvent(
                stream = stubStream(),
                player = null,
                source = null,
                context = linkedMapOf(),
                trigger = ItemScriptTrigger.ASYNC_TICK
            )
            DefaultItemEventBus.post(event)
            assertTrue(event.save)
            assertTrue(event.cancelled)
            assertEquals(1, event.context["injected"])
        } finally {
            subscription.close()
        }
    }

    @Test
    fun `should support specific action event subscription`() {
        val subscription = DefaultItemEventBus.subscribe(ItemSelectActionEvent::class.java) { event ->
            event.save = true
            event.cancelled = true
        }
        try {
            val event = ItemSelectActionEvent(
                stream = stubStream(),
                player = null,
                source = null,
                context = linkedMapOf()
            )
            DefaultItemEventBus.post(event)
            assertEquals(ItemScriptTrigger.SELECT, event.trigger)
            assertTrue(event.save)
            assertTrue(event.cancelled)
        } finally {
            subscription.close()
        }
    }

    @Test
    fun `trigger event cancel helper should update cancellable source`() {
        val source = DummyCancellable()
        val event = ItemActionTriggerEvent(
            stream = stubStream(),
            player = null,
            source = source,
            context = linkedMapOf(),
            trigger = ItemScriptTrigger.INTERACT
        )
        event.cancel()
        assertTrue(event.cancelled)
        assertTrue(source.isCancelled)
    }

    @Test
    fun `trigger event source helper should support typed cast`() {
        val stream = stubStream()
        val event = ItemActionTriggerEvent(
            stream = stream,
            player = null,
            source = "source-value",
            context = linkedMapOf(),
            trigger = ItemScriptTrigger.INTERACT
        )
        assertSame(stream, event.itemStream)
        assertEquals("source-value", event.sourceAs<String>())
        assertEquals("source-value", event.sourceAs(String::class.java))
        assertNull(event.sourceAs<Int>())
        assertNull(event.sourceAs(Int::class.java))
    }

    @Test
    fun `should support select display event mutation`() {
        val subscription = DefaultItemEventBus.subscribe(ItemSelectDisplayEvent::class.java) { event ->
            event.displayId = "alt:display"
        }
        try {
            val event = ItemSelectDisplayEvent(
                stream = stubStream(),
                player = null,
                source = null,
                context = linkedMapOf(),
                displayId = "origin:display",
                display = null
            )
            DefaultItemEventBus.post(event)
            assertEquals("alt:display", event.displayId)
        } finally {
            subscription.close()
        }
    }

    @Test
    fun `should support release display build event mutation`() {
        val subscription = DefaultItemEventBus.subscribe(ItemReleaseDisplayBuildEvent::class.java) { event ->
            event.addName("name", "Mutated")
            event.addLore("desc", "A")
            event.addLore("desc", listOf("B"))
        }
        try {
            val event = ItemReleaseDisplayBuildEvent(
                stream = stubStream(),
                player = null,
                source = null,
                context = linkedMapOf(),
                displayId = "display:id",
                display = ItemDisplay(id = "display:id"),
                name = linkedMapOf(),
                lore = linkedMapOf()
            )
            DefaultItemEventBus.post(event)
            assertEquals("Mutated", event.name["name"])
            assertEquals(listOf("A", "B"), event.lore["desc"])
        } finally {
            subscription.close()
        }
    }

    @Test
    fun `should support build pre event name lore mutation`() {
        val event = ItemBuildPreEvent(
            stream = stubStream(),
            player = null,
            context = linkedMapOf()
        )
        event.addName("item_name", "&6Demo")
        event.addLore("item_description", "Line-1")
        event.addLore("item_description", listOf("Line-2"))

        assertSame(event.stream, event.itemStream)
        assertEquals("&6Demo", event.name["item_name"])
        assertEquals(listOf("Line-1", "Line-2"), event.lore["item_description"])
    }

    @Test
    fun `should support build post event aliases`() {
        val event = ItemBuildPostEvent(
            stream = stubStream(),
            player = null,
            context = linkedMapOf(),
            name = mapOf("item_name" to "&6Demo"),
            lore = mapOf("item_description" to mutableListOf("Line-1"))
        )

        assertSame(event.stream, event.itemStream)
        assertEquals("&6Demo", event.name["item_name"])
        assertEquals(listOf("Line-1"), event.lore["item_description"])
    }

    private fun stubStream(): ItemStream {
        return object : ItemStream {
            override val itemId: String = "test:item"
            override val versionHash: String = "test"
            override val metaHistory: List<String> = emptyList()
            override val runtimeData: Map<String, Any?> = emptyMap()
            override val signals: Set<ItemSignal> = emptySet()

            override fun itemStack(): ItemStack = throwUnsupported()

            override fun snapshot(): ItemStack = throwUnsupported()

            override fun setDisplayName(name: String?): ItemStream = this

            override fun setLore(lines: List<String>): ItemStream = this

            override fun setRuntimeData(key: String, value: Any?): ItemStream = this

            override fun getRuntimeData(key: String): Any? = null

            override fun markSignal(signal: ItemSignal): ItemStream = this

            override fun hasSignal(signal: ItemSignal): Boolean = false

            override fun applyMeta(meta: Meta): ItemStream = this

            override fun snapshotData(): ItemStreamData {
                return ItemStreamData(itemId, versionHash, metaHistory, runtimeData)
            }

            override fun toItemStack(): ItemStack = throwUnsupported()
        }
    }

    private class DummyCancellable : Cancellable {

        private var cancelled = false

        override fun isCancelled(): Boolean {
            return cancelled
        }

        override fun setCancelled(cancel: Boolean) {
            cancelled = cancel
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> throwUnsupported(): T {
        throw UnsupportedOperationException("test stub")
    }
}
