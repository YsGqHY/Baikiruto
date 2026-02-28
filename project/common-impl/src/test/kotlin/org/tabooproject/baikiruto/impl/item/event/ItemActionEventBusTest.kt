package org.tabooproject.baikiruto.impl.item.event

import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemStreamData
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.event.ItemActionTriggerEvent
import org.tabooproject.baikiruto.core.item.event.ItemLifecycleEvent

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

    @Suppress("UNCHECKED_CAST")
    private fun <T> throwUnsupported(): T {
        throw UnsupportedOperationException("test stub")
    }
}
