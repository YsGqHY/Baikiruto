package org.tabooproject.baikiruto.core

data class ScriptCacheStats(
    val cacheSize: Int = 0,
    val invokeHits: Long = 0,
    val invokeMisses: Long = 0,
    val totalCompilations: Long = 0,
    val totalCompilationNanos: Long = 0
) {

    fun hitRate(): Double {
        val total = invokeHits + invokeMisses
        if (total <= 0L) {
            return 0.0
        }
        return invokeHits.toDouble() / total.toDouble()
    }
}
