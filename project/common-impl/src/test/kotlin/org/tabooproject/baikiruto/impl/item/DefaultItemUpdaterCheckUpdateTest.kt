package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tabooproject.baikiruto.core.Baikiruto
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
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemStreamData
import org.tabooproject.baikiruto.core.item.ItemUpdater
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.Registry
import org.tabooproject.baikiruto.core.item.event.ItemCheckUpdateEvent
import org.tabooproject.baikiruto.core.item.event.ItemEventBus
import org.tabooproject.baikiruto.core.item.event.ItemUpdateEvent
import org.tabooproject.baikiruto.impl.item.event.DefaultItemEventBus
import java.util.concurrent.atomic.AtomicInteger

class DefaultItemUpdaterCheckUpdateTest {

    @Test
    fun `should skip update by default when item is not outdated`() {
        val origin = ItemStack(Material.STONE)
        val sourceStream = TestStream("test:item", "v1", origin, linkedMapOf("foo" to "bar"))
        val rebuiltStream = TestStream("test:item", "v1", ItemStack(Material.DIAMOND))
        val buildCount = AtomicInteger(0)
        val item = TestItem("test:item", rebuiltStream, buildCount)

        val previous = installApi(TestApi(sourceStream, item))
        val checks = arrayListOf<ItemCheckUpdateEvent>()
        val updates = arrayListOf<ItemUpdateEvent>()
        val checkSubscription = DefaultItemEventBus.subscribe(ItemCheckUpdateEvent::class.java) { checks += it }
        val updateSubscription = DefaultItemEventBus.subscribe(ItemUpdateEvent::class.java) { updates += it }
        try {
            val result = DefaultItemUpdater.checkUpdate(null, origin)
            assertSame(origin, result)
            assertEquals(1, buildCount.get())
            assertEquals(1, checks.size)
            assertTrue(checks.first().cancelled)
            assertFalse(checks.first().isOutdated)
            assertTrue(updates.isEmpty())
        } finally {
            checkSubscription.close()
            updateSubscription.close()
            restoreApi(previous)
        }
    }

    @Test
    fun `should allow forced rebuild when check update event is uncancelled`() {
        val origin = ItemStack(Material.STONE)
        val sourceStream = TestStream("test:item", "v1", origin, linkedMapOf("foo" to "bar"))
        val rebuiltStream = TestStream("test:item", "v1", ItemStack(Material.DIAMOND))
        val buildCount = AtomicInteger(0)
        val item = TestItem("test:item", rebuiltStream, buildCount)

        val previous = installApi(TestApi(sourceStream, item))
        val checks = arrayListOf<ItemCheckUpdateEvent>()
        val updates = arrayListOf<ItemUpdateEvent>()
        val checkSubscription = DefaultItemEventBus.subscribe(ItemCheckUpdateEvent::class.java) {
            checks += it
            it.cancelled = false
        }
        val updateSubscription = DefaultItemEventBus.subscribe(ItemUpdateEvent::class.java) { updates += it }
        try {
            val result = DefaultItemUpdater.checkUpdate(null, origin)
            assertNotSame(origin, result)
            assertEquals(2, buildCount.get())
            assertEquals(1, checks.size)
            assertFalse(checks.first().cancelled)
            assertFalse(checks.first().isOutdated)
            assertEquals(1, updates.size)
            assertEquals("v1", updates.first().oldVersionHash)
            assertEquals("v1", updates.first().newVersionHash)
            assertEquals("bar", rebuiltStream.runtimeData["foo"])
            assertTrue(rebuiltStream.hasSignal(ItemSignal.UPDATE_CHECKED))
        } finally {
            checkSubscription.close()
            updateSubscription.close()
            restoreApi(previous)
        }
    }

    @Test
    fun `should skip rebuild when outdated item is cancelled by check update event`() {
        val origin = ItemStack(Material.STONE)
        val sourceStream = TestStream("test:item", "old", origin)
        val rebuiltStream = TestStream("test:item", "new", ItemStack(Material.DIAMOND))
        val buildCount = AtomicInteger(0)
        val item = TestItem("test:item", rebuiltStream, buildCount)

        val previous = installApi(TestApi(sourceStream, item))
        val updates = arrayListOf<ItemUpdateEvent>()
        val checkSubscription = DefaultItemEventBus.subscribe(ItemCheckUpdateEvent::class.java) { it.cancelled = true }
        val updateSubscription = DefaultItemEventBus.subscribe(ItemUpdateEvent::class.java) { updates += it }
        try {
            val result = DefaultItemUpdater.checkUpdate(null, origin)
            assertSame(origin, result)
            assertEquals(1, buildCount.get())
            assertTrue(updates.isEmpty())
        } finally {
            checkSubscription.close()
            updateSubscription.close()
            restoreApi(previous)
        }
    }

    private fun installApi(api: BaikirutoAPI): BaikirutoAPI? {
        val field = Baikiruto::class.java.getDeclaredField("api")
        field.isAccessible = true
        val previous = field.get(Baikiruto) as? BaikirutoAPI
        Baikiruto.register(api)
        return previous
    }

    private fun restoreApi(previous: BaikirutoAPI?) {
        val field = Baikiruto::class.java.getDeclaredField("api")
        field.isAccessible = true
        field.set(Baikiruto, previous)
    }

    private class TestApi(
        private val stream: ItemStream?,
        private val item: Item?
    ) : BaikirutoAPI {

        override fun getScriptHandler(): BaikirutoScriptHandler = unsupported()

        override fun getItemManager(): ItemManager = unsupported()

        override fun getItemHandler(): ItemHandler = unsupported()

        override fun getItemLoader(): ItemLoader = unsupported()

        override fun getItemRegistry(): Registry<Item> = unsupported()

        override fun getModelRegistry(): Registry<ItemModel> = unsupported()

        override fun getDisplayRegistry(): Registry<ItemDisplay> = unsupported()

        override fun getGroupRegistry(): Registry<ItemGroup> = unsupported()

        override fun registerItem(item: Item): Item = unsupported()

        override fun getItem(itemId: String): Item? {
            return item?.takeIf { it.id == itemId }
        }

        override fun buildItem(itemId: String, context: Map<String, Any?>): ItemStack? = unsupported()

        override fun readItem(itemStack: ItemStack): ItemStream? {
            return stream
        }

        override fun getItemSerializer(): ItemSerializer = unsupported()

        override fun getItemUpdater(): ItemUpdater {
            return DefaultItemUpdater
        }

        override fun getItemEventBus(): ItemEventBus {
            return DefaultItemEventBus
        }

        private fun <T> unsupported(): T {
            throw UnsupportedOperationException("unused in test")
        }
    }

    private class TestItem(
        override val id: String,
        private val builtStream: TestStream,
        private val buildCount: AtomicInteger
    ) : Item {

        override val metas: List<Meta> = emptyList()

        override fun build(context: Map<String, Any?>): ItemStream {
            buildCount.incrementAndGet()
            return builtStream
        }
    }

    private class TestStream(
        override val itemId: String,
        override val versionHash: String,
        private val stack: ItemStack,
        runtime: MutableMap<String, Any?> = linkedMapOf()
    ) : ItemStream {

        override val metaHistory: List<String> = emptyList()
        private val runtime = runtime
        private val signalSet = linkedSetOf<ItemSignal>()

        override val runtimeData: Map<String, Any?>
            get() = runtime

        override val signals: Set<ItemSignal>
            get() = signalSet

        override fun itemStack(): ItemStack {
            return stack
        }

        override fun snapshot(): ItemStack {
            return stack.clone()
        }

        override fun setDisplayName(name: String?): ItemStream {
            return this
        }

        override fun setLore(lines: List<String>): ItemStream {
            return this
        }

        override fun setRuntimeData(key: String, value: Any?): ItemStream {
            runtime[key] = value
            return this
        }

        override fun getRuntimeData(key: String): Any? {
            return runtime[key]
        }

        override fun markSignal(signal: ItemSignal): ItemStream {
            signalSet += signal
            return this
        }

        override fun hasSignal(signal: ItemSignal): Boolean {
            return signal in signalSet
        }

        override fun applyMeta(meta: Meta): ItemStream {
            return this
        }

        override fun snapshotData(): ItemStreamData {
            return ItemStreamData(itemId, versionHash, metaHistory, runtimeData)
        }

        override fun toItemStack(): ItemStack {
            return stack.clone()
        }
    }
}
