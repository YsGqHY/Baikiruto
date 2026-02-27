package org.tabooproject.baikiruto.impl.ops

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.core.Baikiruto
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
            "versionProfile=${version.profileId}",
            "storageMode=${if (version.dataComponentStorage) "DATA_COMPONENTS" else "LEGACY_NBT"}",
            "customModelData=${version.supportsCustomModelData}",
            "itemModel=${version.supportsItemModel}",
            "loadedItems=${ItemDefinitionLoader.loadedIds().size}",
            "scriptCacheSize=${cacheStats?.cacheSize ?: 0}",
            "scriptCacheHitRate=${cacheHitRate}%",
            "avgItemBuildMicros=${BaikirutoMetrics.itemBuildAverageMicros()}",
            "fluxonAvailable=${isFluxonAvailable()}"
        )
    }

    private fun isFluxonAvailable(): Boolean {
        return runCatching { Class.forName("org.tabooproject.fluxon.Fluxon") }.isSuccess
    }
}
