package org.tabooproject.baikiruto.impl.ops

import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import org.tabooproject.baikiruto.impl.item.ItemScriptPreheatService

object BaikirutoReloader {

    fun reloadAll(): String {
        val startAt = System.currentTimeMillis()
        BaikirutoSettings.conf.reload()
        val loaded = ItemDefinitionLoader.reloadItems("reload-all")
        ItemScriptPreheatService.preheatRegistry()
        val cost = System.currentTimeMillis() - startAt
        return "Reload finished: items=$loaded, cost=${cost}ms"
    }

    fun reloadItems(): String {
        val loaded = ItemDefinitionLoader.reloadItems("reload-items")
        return "Item reload finished: items=$loaded"
    }

    fun reloadScripts(): String {
        ItemDefinitionLoader.loadedIds().forEach { Baikiruto.api().getScriptHandler().invalidateByPrefix(it) }
        ItemScriptPreheatService.preheatRegistry()
        val stats = Baikiruto.api().getScriptHandler().cacheStats()
        return "Script reload finished: cacheSize=${stats.cacheSize}, totalCompilations=${stats.totalCompilations}"
    }
}
