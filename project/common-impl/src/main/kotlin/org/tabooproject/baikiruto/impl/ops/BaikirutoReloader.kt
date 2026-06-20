package org.tabooproject.baikiruto.impl.ops

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import org.tabooproject.baikiruto.impl.item.ItemScriptPreheatService
import taboolib.common.platform.function.console
import taboolib.module.lang.sendLang
import taboolib.platform.util.submit

object BaikirutoReloader {

    data class ReloadResult(
        val items: Int,
        val updatedPlayers: Int,
        val costMs: Long = 0,
        val onlineUpdateEnabled: Boolean = BaikirutoSettings.reloadOnlineUpdateEnabled
    )

    data class ScriptReloadResult(
        val cacheSize: Int,
        val totalCompilations: Long
    )

    fun reloadAll(): ReloadResult {
        val startAt = System.currentTimeMillis()
        BaikirutoSettings.conf.reload()
        val result = reloadItemsInternal("reload-all")
        ItemScriptPreheatService.preheatRegistry()
        val cost = System.currentTimeMillis() - startAt
        return ReloadResult(
            items = result.items,
            updatedPlayers = result.updatedPlayers,
            costMs = cost
        )
    }

    fun reloadItems(): ReloadResult {
        return reloadItemsInternal("reload-items")
    }

    fun reloadItemsFromWatcher(source: String) {
        val result = reloadItemsInternal(source)
        if (result.onlineUpdateEnabled) {
            console().sendLang("log-reload-watcher", result.items, result.updatedPlayers)
        } else {
            console().sendLang("log-reload-watcher-no-update", result.items)
        }
    }

    fun reloadScripts(): ScriptReloadResult {
        ItemDefinitionLoader.loadedIds().forEach { Baikiruto.api().getScriptHandler().invalidateByPrefix(it) }
        ItemScriptPreheatService.preheatRegistry()
        val stats = Baikiruto.api().getScriptHandler().cacheStats()
        return ScriptReloadResult(
            cacheSize = stats.cacheSize,
            totalCompilations = stats.totalCompilations
        )
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
        return Bukkit.getOnlinePlayers().count { player ->
            player.submit {
                if (player.isOnline) {
                    Baikiruto.api().getItemUpdater().checkUpdate(player, player.inventory)
                }
            }
            true
        }
    }
}
