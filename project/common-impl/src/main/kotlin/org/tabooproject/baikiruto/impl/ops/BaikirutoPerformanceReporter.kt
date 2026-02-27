package org.tabooproject.baikiruto.impl.ops

import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.metrics.BaikirutoMetrics
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.service.PlatformExecutor
import taboolib.platform.util.submit

object BaikirutoPerformanceReporter {

    private var task: PlatformExecutor.PlatformTask? = null

    @Awake(LifeCycle.ACTIVE)
    private fun start() {
        if (!BaikirutoSettings.performanceLogEnabled) {
            return
        }
        task?.cancel()
        task = submit(period = 20L * 60L) {
            val cache = runCatching { Baikiruto.api().getScriptHandler().cacheStats() }.getOrNull()
            val rate = cache?.hitRate()?.times(100.0)?.let { "%.2f".format(it) } ?: "0.00"
            info(
                "[Baikiruto] perf: avgBuild=${BaikirutoMetrics.itemBuildAverageMicros()}us, " +
                    "buildCount=${BaikirutoMetrics.itemBuildCount()}, " +
                    "scriptCache=${cache?.cacheSize ?: 0}, hitRate=${rate}%"
            )
        }
    }

    @Awake(LifeCycle.DISABLE)
    private fun stop() {
        task?.cancel()
        task = null
    }
}
