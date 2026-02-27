package org.tabooproject.baikiruto.impl.item

import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemScriptHooks
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.event.ItemBuildPostEvent
import org.tabooproject.baikiruto.core.item.event.ItemBuildPreEvent
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.log.BaikirutoLog
import org.tabooproject.baikiruto.impl.metrics.BaikirutoMetrics
import org.tabooproject.baikiruto.impl.script.item.ItemScriptExecutor
import taboolib.common.platform.function.warning

class DefaultItem(
    override val id: String,
    private val template: ItemStack,
    private val versionHashSupplier: () -> String = { "dev" },
    override val groupId: String? = null,
    override val displayId: String? = null,
    override val modelIds: List<String> = emptyList(),
    override val metas: List<Meta> = emptyList(),
    override val scripts: ItemScriptHooks = ItemScriptHooks(),
    private val defaultRuntimeData: Map<String, Any?> = emptyMap()
) : Item {

    fun latestVersionHash(): String {
        return versionHashSupplier()
    }

    override fun build(context: Map<String, Any?>): ItemStream {
        val startAt = System.nanoTime()
        val executionContext = LinkedHashMap(defaultRuntimeData)
        executionContext.putAll(context)
        val player = context["player"] as? org.bukkit.entity.Player
        val stream = DefaultItemStream(
            backingItem = template.clone(),
            itemId = id,
            versionHash = versionHashSupplier(),
            initialRuntimeData = defaultRuntimeData
        )
        val preEvent = ItemBuildPreEvent(stream, player, executionContext)
        Baikiruto.api().getItemEventBus().post(preEvent)
        if (preEvent.cancelled) {
            return stream
        }
        ItemScriptExecutor.execute(id, ItemScriptTrigger.BUILD, scripts.source(ItemScriptTrigger.BUILD), stream, executionContext)
        metas.forEach { meta ->
            try {
                stream.applyMeta(meta)
                ItemScriptExecutor.execute(
                    itemId = "$id:meta:${meta.id}",
                    trigger = ItemScriptTrigger.BUILD,
                    source = meta.scripts.source(ItemScriptTrigger.BUILD),
                    stream = stream,
                    context = executionContext
                )
            } catch (ex: Throwable) {
                BaikirutoLog.scriptRuntimeFailed("$id:meta:${meta.id}:build", ex)
            }
        }
        Baikiruto.api().getItemEventBus().post(ItemBuildPostEvent(stream, player, executionContext))
        val costNanos = System.nanoTime() - startAt
        BaikirutoMetrics.recordItemBuild(costNanos)
        if (BaikirutoSettings.performanceLogEnabled) {
            val costMillis = costNanos / 1_000_000L
            if (costMillis >= BaikirutoSettings.slowBuildMillis) {
                warning("[Baikiruto] Slow item build detected: id=$id, cost=${costMillis}ms")
            }
        }
        return stream
    }

    override fun drop(stream: ItemStream, context: Map<String, Any?>) {
        metas.reversed().forEach { meta ->
            try {
                meta.drop(stream)
                ItemScriptExecutor.execute(
                    itemId = "$id:meta:${meta.id}",
                    trigger = ItemScriptTrigger.DROP,
                    source = meta.scripts.source(ItemScriptTrigger.DROP),
                    stream = stream,
                    context = context
                )
            } catch (ex: Throwable) {
                BaikirutoLog.scriptRuntimeFailed("$id:meta:${meta.id}:drop", ex)
            }
        }
        ItemScriptExecutor.execute(id, ItemScriptTrigger.DROP, scripts.source(ItemScriptTrigger.DROP), stream, context)
    }
}
