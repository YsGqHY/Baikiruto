package org.tabooproject.baikiruto.impl.metrics

import java.util.concurrent.atomic.AtomicLong

object BaikirutoMetrics {

    private val itemBuildCount = AtomicLong(0)
    private val itemBuildTotalNanos = AtomicLong(0)

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
}
