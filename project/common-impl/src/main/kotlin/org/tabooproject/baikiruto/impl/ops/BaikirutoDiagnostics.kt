package org.tabooproject.baikiruto.impl.ops

import org.bukkit.Bukkit
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.hook.HeadDatabaseHook
import org.tabooproject.baikiruto.impl.item.ItemDefinitionLoader
import org.tabooproject.baikiruto.impl.metrics.BaikirutoMetrics
import org.tabooproject.baikiruto.impl.script.FluxonChecker
import org.tabooproject.baikiruto.impl.version.VersionAdapterService

object BaikirutoDiagnostics {

    /**
     * 返回诊断信息列表。
     * 输出为 key=value 技术格式，面向管理员/开发者，有意不通过 lang 系统国际化。
     */
    fun lines(): List<String> {
        val cacheStats = Baikiruto.apiOrNull()?.getScriptHandler()?.cacheStats()
        val cacheHitRate = cacheStats?.hitRate()?.times(100.0)?.let { "%.2f".format(it) } ?: "0.00"
        val version = VersionAdapterService.currentProfile()
        val registeredItems = BaikirutoMetrics.registeredItemCount()
        val registeredScripts = BaikirutoMetrics.registeredScriptCount()
        val registeredModels = BaikirutoMetrics.registeredModelCount()
        val registeredDisplays = BaikirutoMetrics.registeredDisplayCount()
        val registeredGroups = BaikirutoMetrics.registeredGroupCount()
        return listOf(
            "server=${serverVersion()}",
            "scriptEngine=FLUXON_ONLY",
            "versionProfile=${version.profileId}",
            "storageMode=${if (version.dataComponentStorage) "DATA_COMPONENTS" else "LEGACY_NBT"}",
            "customModelData=${version.supportsCustomModelData}",
            "itemModel=${version.supportsItemModel}",
            "loadedItems=${ItemDefinitionLoader.loadedIds().size}",
            "registeredItems=$registeredItems",
            "registeredScripts=$registeredScripts",
            "registeredModels=$registeredModels",
            "registeredDisplays=$registeredDisplays",
            "registeredGroups=$registeredGroups",
            "scriptCacheSize=${cacheStats?.cacheSize ?: 0}",
            "scriptCacheHitRate=${cacheHitRate}%",
            "avgItemBuildMicros=${BaikirutoMetrics.itemBuildAverageMicros()}",
            "fluxonAvailable=${FluxonChecker.isReady()}",
            "fluxonSource=${FluxonChecker.sourceId()}",
            "fluxonBundledAvailable=${FluxonChecker.isBundledAvailable()}",
            "fluxonBootstrapFailure=${FluxonChecker.startupFailureMessage() ?: "none"}",
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

    private fun serverVersion(): String {
        return try {
            Bukkit.getBukkitVersion()
        } catch (_: Throwable) {
            "unknown"
        }
    }

    private fun isClassAvailable(name: String): Boolean {
        return try {
            Class.forName(name, false, javaClass.classLoader)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
