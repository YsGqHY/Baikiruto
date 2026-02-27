package org.tabooproject.baikiruto.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptCacheStatsTest {

    @Test
    fun `should return zero hit rate when no invocation exists`() {
        val stats = ScriptCacheStats()
        assertEquals(0.0, stats.hitRate())
    }

    @Test
    fun `should calculate hit rate from hits and misses`() {
        val stats = ScriptCacheStats(invokeHits = 6, invokeMisses = 4)
        assertEquals(0.6, stats.hitRate())
    }
}
