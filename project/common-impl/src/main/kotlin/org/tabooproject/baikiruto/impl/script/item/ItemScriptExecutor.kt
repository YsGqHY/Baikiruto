package org.tabooproject.baikiruto.impl.script.item

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.item.DefaultItemStream
import org.tabooproject.baikiruto.impl.log.BaikirutoLog

object ItemScriptExecutor {

    fun execute(
        itemId: String,
        trigger: ItemScriptTrigger,
        source: String?,
        stream: ItemStream,
        context: Map<String, Any?> = emptyMap()
    ): Any? {
        if (source.isNullOrBlank()) {
            return null
        }
        val sender = context["sender"] as? CommandSender
        val streamImpl = stream as? DefaultItemStream
        streamImpl?.rememberInvocationContext(context)
        val providedItem = stream.snapshot()
        val providedItemBaseline = providedItem.clone()
        val variables = LinkedHashMap<String, Any?>()
        variables.putAll(context)
        variables["sender"] = sender
        val player = context["player"] as? Player ?: sender as? Player
        variables["player"] = player
        variables["item"] = providedItem
        variables["stream"] = stream
        variables["ops"] = ItemScriptOps(stream, player)
        variables["event"] = context["event"]
        variables["ctx"] = context
        variables["itemId"] = itemId
        variables["trigger"] = trigger.name.lowercase()
        return try {
            Baikiruto.api().getScriptHandler()
                .invoke(source, "$itemId:${trigger.name.lowercase()}", sender, variables)
                .also { result ->
                    if (streamImpl != null && providedItem != providedItemBaseline) {
                        streamImpl.syncScriptResult(providedItem, providedItem, providedItemBaseline)
                    }
                    if (streamImpl != null && result != null) {
                        streamImpl.syncScriptResult(result, providedItem, providedItemBaseline)
                    }
                }
        } catch (ex: Throwable) {
            BaikirutoLog.scriptRuntimeFailed("$itemId:${trigger.name.lowercase()}", ex)
            null
        }
    }

    fun preheat(item: Item, batchSize: Int) {
        val effectiveBatchSize = batchSize.coerceAtLeast(1)
        val scripts = item.collectScripts().entries.toList()
        scripts.chunked(effectiveBatchSize).forEach { batch ->
            batch.forEach { (scriptId, source) ->
                try {
                    Baikiruto.api().getScriptHandler().preheat(source, scriptId)
                } catch (ex: Throwable) {
                    BaikirutoLog.scriptCompileFailed(scriptId, ex)
                }
            }
        }
    }

    fun invalidate(itemId: String) {
        try {
            Baikiruto.api().getScriptHandler().invalidateByPrefix(itemId)
        } catch (ex: Throwable) {
            BaikirutoLog.scriptRuntimeFailed("$itemId:invalidate", ex)
        }
    }
}
