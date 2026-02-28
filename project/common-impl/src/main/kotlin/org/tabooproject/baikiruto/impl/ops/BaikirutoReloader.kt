package org.tabooproject.baikiruto.impl.ops

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import org.tabooproject.baikiruto.impl.item.ItemScriptPreheatService
import taboolib.common.platform.function.info

object BaikirutoReloader {

    fun reloadAll(): String {
        val startAt = System.currentTimeMillis()
        BaikirutoSettings.conf.reload()
        val result = reloadItemsInternal("reload-all")
        ItemScriptPreheatService.preheatRegistry()
        val cost = System.currentTimeMillis() - startAt
        return if (BaikirutoSettings.reloadOnlineUpdateEnabled) {
            "Reload finished: items=${result.items}, onlineUpdated=${result.updatedPlayers}, cost=${cost}ms"
        } else {
            "Reload finished: items=${result.items}, cost=${cost}ms"
        }
    }

    fun reloadItems(): String {
        val result = reloadItemsInternal("reload-items")
        return if (BaikirutoSettings.reloadOnlineUpdateEnabled) {
            "Item reload finished: items=${result.items}, onlineUpdated=${result.updatedPlayers}"
        } else {
            "Item reload finished: items=${result.items}"
        }
    }

    fun reloadItemsFromWatcher(source: String) {
        val result = reloadItemsInternal(source)
        if (BaikirutoSettings.reloadOnlineUpdateEnabled) {
            info("[Baikiruto] Watcher reload finished: items=${result.items}, onlineUpdated=${result.updatedPlayers}")
        } else {
            info("[Baikiruto] Watcher reload finished: items=${result.items}")
        }
    }

    fun reloadScripts(): String {
        ItemDefinitionLoader.loadedIds().forEach { Baikiruto.api().getScriptHandler().invalidateByPrefix(it) }
        ItemScriptPreheatService.preheatRegistry()
        val stats = Baikiruto.api().getScriptHandler().cacheStats()
        return "Script reload finished: cacheSize=${stats.cacheSize}, totalCompilations=${stats.totalCompilations}"
    }

    private fun reloadItemsInternal(source: String): ReloadResult {
        val loaded = ItemDefinitionLoader.reloadItems(source)
        val updated = updateOnlineInventoriesIfEnabled()
        return ReloadResult(items = loaded, updatedPlayers = updated)
    }

    private fun updateOnlineInventoriesIfEnabled(): Int {
        if (!BaikirutoSettings.reloadOnlineUpdateEnabled) {
            return 0
        }
        return Bukkit.getOnlinePlayers().sumOf { player ->
            Baikiruto.api().getItemUpdater().checkUpdate(player, player.inventory)
        }
    }

    private data class ReloadResult(
        val items: Int,
        val updatedPlayers: Int
    )
}
