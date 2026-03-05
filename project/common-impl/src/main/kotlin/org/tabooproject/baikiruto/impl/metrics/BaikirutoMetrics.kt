package org.tabooproject.baikiruto.impl.metrics

import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.common.platform.Platform
import taboolib.common.platform.function.info
import taboolib.common.platform.function.pluginVersion
import taboolib.common.platform.function.warning
import taboolib.module.metrics.Metrics
import taboolib.module.metrics.charts.SingleLineChart
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object BaikirutoMetrics {

    private const val bStatsPluginId = 29903

    private val itemBuildCount = AtomicLong(0)
    private val itemBuildTotalNanos = AtomicLong(0)
    private val bStatsInitialized = AtomicBoolean(false)
    private var bStats: Metrics? = null

    fun initializeBStats() {
        if (!bStatsInitialized.compareAndSet(false, true)) {
            return
        }
        runCatching {
            Metrics(bStatsPluginId, pluginVersion, Platform.BUKKIT).also { metrics ->
                metrics.addCustomChart(SingleLineChart("registered_items") { registeredItemCount() })
                metrics.addCustomChart(SingleLineChart("registered_scripts") { registeredScriptCount() })
                metrics.addCustomChart(SingleLineChart("registered_models") { registeredModelCount() })
                metrics.addCustomChart(SingleLineChart("registered_displays") { registeredDisplayCount() })
                metrics.addCustomChart(SingleLineChart("registered_groups") { registeredGroupCount() })
                metrics.addCustomChart(SingleLineChart("script_cache_size") { scriptCacheSize() })
                metrics.addCustomChart(SingleLineChart("item_build_total") { toChartValue(itemBuildCount()) })
                metrics.addCustomChart(SingleLineChart("item_build_avg_micros") { toChartValue(itemBuildAverageMicros()) })
            }.also {
                bStats = it
                info("[Baikiruto] bStats metrics enabled (pluginId=$bStatsPluginId).")
            }
        }.onFailure { ex ->
            warning("[Baikiruto] Failed to initialize bStats metrics: ${ex.message}")
        }
    }

    fun recordItemBuild(costNanos: Long) {
        itemBuildCount.incrementAndGet()
        itemBuildTotalNanos.addAndGet(costNanos)
    }

    fun itemBuildCount(): Long {
        return itemBuildCount.get()
    }

    fun itemBuildAverageMicros(): Long {
        val count = itemBuildCount.get()
        if (count <= 0L) {
            return 0
        }
        return (itemBuildTotalNanos.get() / count) / 1_000L
    }

    fun registeredItemCount(): Int {
        return runCatching {
            Baikiruto.api().getItemRegistry().keys().size
        }.getOrDefault(0)
    }

    fun registeredScriptCount(): Int {
        return runCatching {
            Baikiruto.api().getItemRegistry().values()
                .sumOf { item -> item.collectScripts().size }
        }.getOrDefault(0)
    }

    fun registeredModelCount(): Int {
        return runCatching {
            Baikiruto.api().getModelRegistry().keys().size
        }.getOrDefault(0)
    }

    fun registeredDisplayCount(): Int {
        return runCatching {
            Baikiruto.api().getDisplayRegistry().keys().size
        }.getOrDefault(0)
    }

    fun registeredGroupCount(): Int {
        return runCatching {
            Baikiruto.api().getGroupRegistry().keys().size
        }.getOrDefault(0)
    }

    fun scriptCacheSize(): Int {
        return runCatching {
            Baikiruto.api().getScriptHandler().cacheStats().cacheSize
        }.getOrDefault(0)
    }

    private fun toChartValue(value: Long): Int {
        return value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
