package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemScriptHooks
import org.tabooproject.baikiruto.impl.script.item.ItemScriptExecutor

object ItemScriptActionDispatcher {

    fun hasAction(item: Item, trigger: ItemScriptTrigger, context: Map<String, Any?> = emptyMap()): Boolean {
        val executionContext = mergeContext(item, context)
        val locale = resolveLocale(executionContext)
        if (trigger == ItemScriptTrigger.DROP) {
            return item.scripts.has(trigger, locale) ||
                item.scripts.shouldCancel(trigger, locale) ||
                item.metas.isNotEmpty()
        }
        if (item.scripts.has(trigger, locale) || item.scripts.shouldCancel(trigger, locale)) {
            return true
        }
        return item.metas.any { meta ->
            meta.scripts.has(trigger, locale) || meta.scripts.shouldCancel(trigger, locale)
        }
    }

    fun dispatch(item: Item, trigger: ItemScriptTrigger, stream: ItemStream, context: Map<String, Any?> = emptyMap()) {
        val executionContext = mergeContext(item, context)
        val locale = resolveLocale(executionContext)
        val event = executionContext["event"]
        if (trigger == ItemScriptTrigger.DROP) {
            applyCancellationIfNeeded(item.scripts, trigger, locale, event)
            item.metas.forEach { meta ->
                applyCancellationIfNeeded(meta.scripts, trigger, locale, event)
            }
            item.drop(stream, executionContext)
            stream.markSignal(ItemSignal.SCRIPT_DISPATCHED)
            return
        }
        applyCancellationIfNeeded(item.scripts, trigger, locale, event)
        ItemScriptExecutor.execute(
            itemId = item.id,
            trigger = trigger,
            source = item.scripts.source(trigger, locale),
            stream = stream,
            context = executionContext
        )
        item.metas.forEach { meta ->
            applyCancellationIfNeeded(meta.scripts, trigger, locale, event)
            ItemScriptExecutor.execute(
                itemId = "${item.id}:meta:${meta.id}",
                trigger = trigger,
                source = meta.scripts.source(trigger, locale),
                stream = stream,
                context = executionContext
            )
        }
        stream.markSignal(ItemSignal.SCRIPT_DISPATCHED)
    }

    private fun applyCancellationIfNeeded(
        hooks: ItemScriptHooks,
        trigger: ItemScriptTrigger,
        locale: String?,
        event: Any?
    ) {
        if (!hooks.shouldCancel(trigger, locale)) {
            return
        }
        val cancellable = event as? Cancellable ?: return
        cancellable.isCancelled = true
    }

    private fun mergeContext(item: Item, context: Map<String, Any?>): Map<String, Any?> {
        if (item.eventData.isEmpty()) {
            return context
        }
        return LinkedHashMap<String, Any?>().apply {
            putAll(item.eventData)
            putAll(context)
        }
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
