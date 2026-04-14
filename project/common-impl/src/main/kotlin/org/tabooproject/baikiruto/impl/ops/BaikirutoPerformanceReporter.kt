package org.tabooproject.baikiruto.impl.ops

import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.metrics.BaikirutoMetrics
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.console
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.module.lang.sendLang

object BaikirutoPerformanceReporter {

    private var task: PlatformExecutor.PlatformTask? = null

    @Awake(LifeCycle.ACTIVE)
    private fun start() {
        if (!BaikirutoSettings.performanceLogEnabled) {
            return
        }
        task?.cancel()
        task = submit(period = 20L * 60L) {
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

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        task?.cancel()
        task = null
    }
}
