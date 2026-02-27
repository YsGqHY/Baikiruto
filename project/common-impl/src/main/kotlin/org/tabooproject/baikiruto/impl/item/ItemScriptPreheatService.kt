package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.script.item.ItemScriptExecutor
import taboolib.common.platform.function.info

object ItemScriptPreheatService {

    fun preheatIfEnabled(items: Collection<Item>) {
        if (!BaikirutoSettings.scriptPreheatEnabled) {
            return
        }
        if (!BaikirutoSettings.scriptPreheatStrategy.equals("ON_ENABLE", ignoreCase = true) &&
            !BaikirutoSettings.scriptPreheatStrategy.equals("ON_RELOAD", ignoreCase = true)
        ) {
            return
        }
        val batchSize = BaikirutoSettings.scriptPreheatBatchSize.coerceAtLeast(1)
        items.forEach { ItemScriptExecutor.preheat(it, batchSize) }
        info("[Baikiruto] Script preheat completed for ${items.size} items.")
    }

    fun preheatRegistry() {
        val registry = Baikiruto.api().getItemRegistry()
        preheatIfEnabled(registry.values())
    }

    fun invalidateItemScripts(itemId: String) {
        ItemScriptExecutor.invalidate(itemId)
    }
}
