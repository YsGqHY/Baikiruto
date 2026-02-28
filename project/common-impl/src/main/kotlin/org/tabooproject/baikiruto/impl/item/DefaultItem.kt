package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
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
    override val eventData: Map<String, Any?> = emptyMap(),
    private val defaultRuntimeData: Map<String, Any?> = emptyMap()
) : Item {

    fun latestVersionHash(): String {
        return versionHashSupplier()
    }

    override fun build(context: Map<String, Any?>): ItemStream {
        val startAt = System.nanoTime()
        val executionContext = LinkedHashMap(defaultRuntimeData)
        executionContext.putAll(eventData)
        executionContext.putAll(context)
        val player = executionContext["player"] as? Player
        val locale = resolveLocale(executionContext, player)
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
        ItemScriptExecutor.execute(
            id,
            ItemScriptTrigger.BUILD,
            scripts.source(ItemScriptTrigger.BUILD, locale),
            stream,
            executionContext
        )
        metas.forEach { meta ->
            try {
                stream.applyMeta(meta)
                ItemScriptExecutor.execute(
                    itemId = "$id:meta:${meta.id}",
                    trigger = ItemScriptTrigger.BUILD,
                    source = meta.scripts.source(ItemScriptTrigger.BUILD, locale),
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
        val executionContext = LinkedHashMap<String, Any?>()
        executionContext.putAll(eventData)
        executionContext.putAll(context)
        val locale = resolveLocale(executionContext, executionContext["player"] as? Player)
        metas.reversed().forEach { meta ->
            try {
                meta.drop(stream)
                ItemScriptExecutor.execute(
                    itemId = "$id:meta:${meta.id}",
                    trigger = ItemScriptTrigger.DROP,
                    source = meta.scripts.source(ItemScriptTrigger.DROP, locale),
                    stream = stream,
                    context = executionContext
                )
            } catch (ex: Throwable) {
                BaikirutoLog.scriptRuntimeFailed("$id:meta:${meta.id}:drop", ex)
            }
        }
        ItemScriptExecutor.execute(
            id,
            ItemScriptTrigger.DROP,
            scripts.source(ItemScriptTrigger.DROP, locale),
            stream,
            executionContext
        )
    }

    private fun resolveLocale(context: Map<String, Any?>, player: Player?): String? {
        val direct = context["locale"] as? String
        if (!direct.isNullOrBlank()) {
            return normalizeLocale(direct)
        }
        val fallbackPlayer = player ?: context["sender"] as? Player
        return normalizeLocale(runCatching { fallbackPlayer?.locale }.getOrNull())
    }

    private fun normalizeLocale(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('-', '_')
            ?.lowercase()
    }
}
