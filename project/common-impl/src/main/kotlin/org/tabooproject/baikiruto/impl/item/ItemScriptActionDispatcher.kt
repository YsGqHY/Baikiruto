package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.script.item.ItemScriptExecutor

object ItemScriptActionDispatcher {

    fun hasAction(item: Item, trigger: ItemScriptTrigger): Boolean {
        if (trigger == ItemScriptTrigger.DROP) {
            return item.scripts.has(trigger) || item.metas.isNotEmpty()
        }
        if (item.scripts.has(trigger)) {
            return true
        }
        return item.metas.any { meta -> meta.scripts.has(trigger) }
    }

    fun dispatch(item: Item, trigger: ItemScriptTrigger, stream: ItemStream, context: Map<String, Any?> = emptyMap()) {
        if (trigger == ItemScriptTrigger.DROP) {
            item.drop(stream, context)
            stream.markSignal(ItemSignal.SCRIPT_DISPATCHED)
            return
        }
        ItemScriptExecutor.execute(
            itemId = item.id,
            trigger = trigger,
            source = item.scripts.source(trigger),
            stream = stream,
            context = context
        )
        item.metas.forEach { meta ->
            ItemScriptExecutor.execute(
                itemId = "${item.id}:meta:${meta.id}",
                trigger = trigger,
                source = meta.scripts.source(trigger),
                stream = stream,
                context = context
            )
        }
        stream.markSignal(ItemSignal.SCRIPT_DISPATCHED)
    }
}
