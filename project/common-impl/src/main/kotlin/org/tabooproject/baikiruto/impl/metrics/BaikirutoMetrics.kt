package org.tabooproject.baikiruto.impl.metrics

import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.common.platform.Platform
import taboolib.common.platform.function.console
import taboolib.common.platform.function.pluginVersion
import taboolib.module.lang.sendLang
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
        try {
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
                console().sendLang("log-bstats-enabled", bStatsPluginId)
            }
        } catch (ex: Exception) {
            console().sendLang("log-bstats-failed", ex.message.orEmpty())
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
        return readMetric { it.getItemRegistry().keys().size }
    }

    fun registeredScriptCount(): Int {
        return readMetric { api ->
            api.getItemRegistry().values()
                .sumOf { item -> item.collectScripts().size }
        }
    }

    fun registeredModelCount(): Int {
        return readMetric { it.getModelRegistry().keys().size }
    }

    fun registeredDisplayCount(): Int {
        return readMetric { it.getDisplayRegistry().keys().size }
    }

    fun registeredGroupCount(): Int {
        return readMetric { it.getGroupRegistry().keys().size }
    }

    fun scriptCacheSize(): Int {
        return readMetric { it.getScriptHandler().cacheStats().cacheSize }
    }

    private inline fun readMetric(block: (org.tabooproject.baikiruto.core.BaikirutoAPI) -> Int): Int {
        val api = Baikiruto.apiOrNull() ?: return 0
        return block(api)
    }

    private fun toChartValue(value: Long): Int {
        return value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}
