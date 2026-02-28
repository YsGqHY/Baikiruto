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
        val preEvent = ItemBuildPreEvent(
            stream = stream,
            player = player,
            context = executionContext,
            name = resolveBuildNameVariables(executionContext["name"]),
            lore = resolveBuildLoreVariables(executionContext["lore"])
        )
        Baikiruto.api().getItemEventBus().post(preEvent)
        if (preEvent.cancelled) {
            return stream
        }
        syncBuildDisplayVariables(stream, preEvent.name, preEvent.lore)
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
        val postName = resolveBuildNameVariables(stream.getRuntimeData("name"))
        val postLore = resolveBuildLoreVariables(stream.getRuntimeData("lore"))
        Baikiruto.api().getItemEventBus().post(
            ItemBuildPostEvent(
                stream = stream,
                player = player,
                context = executionContext,
                name = postName.toMap(),
                lore = postLore.mapValues { (_, value) -> value.toMutableList() }
            )
        )
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

    private fun syncBuildDisplayVariables(
        stream: ItemStream,
        name: Map<String, String>,
        lore: Map<String, List<String>>
    ) {
        if (name.isNotEmpty()) {
            stream.setRuntimeData("name", LinkedHashMap(name))
        }
        if (lore.isNotEmpty()) {
            stream.setRuntimeData("lore", lore.mapValues { (_, value) -> value.toList() })
        }
    }

    private fun resolveBuildNameVariables(source: Any?): MutableMap<String, String> {
        return when (source) {
            is Map<*, *> -> source.entries.mapNotNull { (key, value) ->
                val normalized = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                normalized to value?.toString().orEmpty()
            }.toMap(linkedMapOf())
                .toMutableMap()
            is String -> source.trim().takeIf { it.isNotEmpty() }
                ?.let { linkedMapOf("item_name" to it) }
                ?: linkedMapOf()
            else -> linkedMapOf()
        }
    }

    private fun resolveBuildLoreVariables(source: Any?): MutableMap<String, MutableList<String>> {
        return when (source) {
            is Map<*, *> -> source.entries.mapNotNull { (key, value) ->
                val normalized = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val lines = toStringList(value)
                if (lines.isEmpty()) {
                    return@mapNotNull null
                }
                normalized to lines.toMutableList()
            }.toMap(linkedMapOf())
                .toMutableMap()
            is String, is Iterable<*> -> {
                val lines = toStringList(source)
                if (lines.isEmpty()) {
                    linkedMapOf()
                } else {
                    linkedMapOf("item_description" to lines.toMutableList())
                }
            }
            else -> linkedMapOf()
        }
    }

    private fun toStringList(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> source.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            is Iterable<*> -> source.flatMap { toStringList(it) }
            else -> listOf(source.toString())
        }
    }
}
