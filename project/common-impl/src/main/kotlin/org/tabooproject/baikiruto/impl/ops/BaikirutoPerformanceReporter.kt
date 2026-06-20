package org.tabooproject.baikiruto.impl.ops

import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.metrics.BaikirutoMetrics
import taboolib.common.platform.Schedule
import taboolib.common.platform.function.console
import taboolib.module.lang.sendLang

object BaikirutoPerformanceReporter {

    @Schedule(period = 20L * 60L)
    private fun report() {
        if (!BaikirutoSettings.performanceLogEnabled) {
            return
        }
        val cache = Baikiruto.apiOrNull()?.getScriptHandler()?.cacheStats()
        val rate = cache?.hitRate()?.times(100.0)?.let { "%.2f".format(it) } ?: "0.00"
        console().sendLang(
            "log-perf-report",
            BaikirutoMetrics.itemBuildAverageMicros(),
            BaikirutoMetrics.itemBuildCount(),
            cache?.cacheSize ?: 0,
            rate
        )
    }
}
