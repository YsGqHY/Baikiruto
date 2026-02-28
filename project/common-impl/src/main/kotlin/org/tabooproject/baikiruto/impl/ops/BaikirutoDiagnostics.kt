package org.tabooproject.baikiruto.impl.ops

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.hook.HeadDatabaseHook
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import org.tabooproject.baikiruto.impl.metrics.BaikirutoMetrics
import org.tabooproject.baikiruto.impl.version.VersionAdapterService

object BaikirutoDiagnostics {

    fun lines(): List<String> {
        val cacheStats = runCatching { Baikiruto.api().getScriptHandler().cacheStats() }.getOrNull()
        val cacheHitRate = cacheStats?.hitRate()?.times(100.0)?.let { "%.2f".format(it) } ?: "0.00"
        val version = VersionAdapterService.currentProfile()
        return listOf(
            "server=${runCatching { Bukkit.getBukkitVersion() }.getOrDefault("unknown")}",
            "scriptEngine=FLUXON_ONLY",
            "versionProfile=${version.profileId}",
            "storageMode=${if (version.dataComponentStorage) "DATA_COMPONENTS" else "LEGACY_NBT"}",
            "customModelData=${version.supportsCustomModelData}",
            "itemModel=${version.supportsItemModel}",
            "loadedItems=${ItemDefinitionLoader.loadedIds().size}",
            "scriptCacheSize=${cacheStats?.cacheSize ?: 0}",
            "scriptCacheHitRate=${cacheHitRate}%",
            "avgItemBuildMicros=${BaikirutoMetrics.itemBuildAverageMicros()}",
            "fluxonAvailable=${isFluxonAvailable()}",
            "hookMythicConfigured=${BaikirutoSettings.mythicHookEnabled}",
            "hookMythicAvailable=${isClassAvailable("ink.ptms.um.event.MobSpawnEvent")}",
            "hookAttributePlusConfigured=${BaikirutoSettings.attributePlusHookEnabled}",
            "hookAttributePlusAvailable=${isClassAvailable("org.serverct.ersha.api.event.AttrUpdateAttributeEvent")}",
            "hookHeadDatabaseConfigured=${BaikirutoSettings.headDatabaseHookEnabled}",
            "hookHeadDatabaseAvailable=${HeadDatabaseHook.isHookAvailable()}",
            "hookHeadDatabaseLoaded=${HeadDatabaseHook.isDatabaseLoaded()}",
            "playerDataStorage=${if (BaikirutoSettings.databaseEnabled) "MYSQL" else "SQLITE"}",
            "playerDataInitialized=${BaikirutoPlayerDataService.isInitialized()}"
        )
    }

    private fun isFluxonAvailable(): Boolean {
        return isClassAvailable("org.tabooproject.fluxon.Fluxon")
    }

    private fun isClassAvailable(name: String): Boolean {
        return runCatching { Class.forName(name) }.isSuccess
    }
}
