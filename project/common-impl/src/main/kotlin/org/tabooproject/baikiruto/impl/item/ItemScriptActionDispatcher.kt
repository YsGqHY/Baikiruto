package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.script.item.ItemScriptExecutor

object ItemScriptActionDispatcher {

    fun hasAction(item: Item, trigger: ItemScriptTrigger, context: Map<String, Any?> = emptyMap()): Boolean {
        val locale = resolveLocale(context)
        if (trigger == ItemScriptTrigger.DROP) {
            return item.scripts.has(trigger, locale) || item.metas.isNotEmpty()
        }
        if (item.scripts.has(trigger, locale)) {
            return true
        }
        return item.metas.any { meta -> meta.scripts.has(trigger, locale) }
    }

    fun dispatch(item: Item, trigger: ItemScriptTrigger, stream: ItemStream, context: Map<String, Any?> = emptyMap()) {
        val locale = resolveLocale(context)
        if (trigger == ItemScriptTrigger.DROP) {
            item.drop(stream, context)
            stream.markSignal(ItemSignal.SCRIPT_DISPATCHED)
            return
        }
        ItemScriptExecutor.execute(
            itemId = item.id,
            trigger = trigger,
            source = item.scripts.source(trigger, locale),
            stream = stream,
            context = context
        )
        item.metas.forEach { meta ->
            ItemScriptExecutor.execute(
                itemId = "${item.id}:meta:${meta.id}",
                trigger = trigger,
                source = meta.scripts.source(trigger, locale),
                stream = stream,
                context = context
            )
        }
        stream.markSignal(ItemSignal.SCRIPT_DISPATCHED)
    }

    private fun resolveLocale(context: Map<String, Any?>): String? {
        val direct = context["locale"] as? String
        if (!direct.isNullOrBlank()) {
            return normalizeLocale(direct)
        }
        val player = context["player"] as? Player ?: context["sender"] as? Player ?: return null
        val locale = runCatching { player.locale }.getOrNull()
        return normalizeLocale(locale)
    }

    private fun normalizeLocale(value: String?): String? {
        return value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('-', '_')
            ?.lowercase()
    }
}
