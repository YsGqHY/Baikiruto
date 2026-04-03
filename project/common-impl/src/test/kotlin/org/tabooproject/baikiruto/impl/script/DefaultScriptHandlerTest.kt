package org.tabooproject.baikiruto.impl.script

import org.bukkit.command.CommandSender
import org.tabooproject.baikiruto.core.BaikirutoScriptSource
import org.tabooproject.baikiruto.core.BaikirutoScriptType
import org.tabooproject.baikiruto.core.ScriptCacheStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultScriptHandlerTest {

    @Test
    fun `should dispatch legacy string scripts to fluxon registry entry`() {
        val handler = DefaultScriptHandler()
        val fluxonType = RecordingScriptType(id = BaikirutoScriptSource.DEFAULT_TYPE, invokeResult = "legacy-ok")
        handler.registerScriptType(fluxonType)

        val result = handler.invoke(" println('ok') ", "demo:build", null)
        handler.preheat(" println('warmup') ", "demo:preheat")

        assertEquals("legacy-ok", result)
        assertEquals("println('ok')", fluxonType.invoked.single().content)
        assertEquals("demo:build", fluxonType.invoked.single().scriptId)
        assertEquals("println('warmup')", fluxonType.preheated.single().content)
        assertEquals("demo:preheat", fluxonType.preheated.single().scriptId)
    }

    @Test
    fun `should dispatch typed sources and aggregate cache stats`() {
        val handler = DefaultScriptHandler()
        val alpha = RecordingScriptType(
            id = "alpha",
            stats = ScriptCacheStats(cacheSize = 2, invokeHits = 3, invokeMisses = 1, totalCompilations = 4, totalCompilationNanos = 5)
        )
        val beta = RecordingScriptType(
            id = "beta",
            stats = ScriptCacheStats(cacheSize = 7, invokeHits = 11, invokeMisses = 13, totalCompilations = 17, totalCompilationNanos = 19),
            invokeResult = "beta-ok"
        )
        handler.registerScriptType(alpha)
        handler.registerScriptType(beta)

        val result = handler.invoke(BaikirutoScriptSource(type = "beta", content = "run()"), "demo:run", null, mapOf("x" to 1))
        handler.preheat(BaikirutoScriptSource(type = "alpha", content = "warmup()"), "demo:warmup")
        handler.invalidate("demo:item")
        handler.invalidateByPrefix("demo:")

        val stats = handler.cacheStats()
        assertEquals("beta-ok", result)
        assertEquals("run()", beta.invoked.single().content)
        assertEquals("warmup()", alpha.preheated.single().content)
        assertEquals(listOf("demo:item"), alpha.invalidated)
        assertEquals(listOf("demo:item"), beta.invalidated)
        assertEquals(listOf("demo:"), alpha.invalidatedByPrefix)
        assertEquals(listOf("demo:"), beta.invalidatedByPrefix)
        assertEquals(9, stats.cacheSize)
        assertEquals(14, stats.invokeHits)
        assertEquals(14, stats.invokeMisses)
        assertEquals(21, stats.totalCompilations)
        assertEquals(24, stats.totalCompilationNanos)
        assertNotNull(handler.getScriptType("alpha"))
        assertTrue(handler.getScriptTypeRegistry().contains("beta"))
    }

    private class RecordingScriptType(
        override val id: String,
        private val stats: ScriptCacheStats = ScriptCacheStats(),
        private val invokeResult: Any? = null
    ) : BaikirutoScriptType {

        val invoked = mutableListOf<Invocation>()
        val preheated = mutableListOf<Invocation>()
        val invalidated = mutableListOf<String>()
        val invalidatedByPrefix = mutableListOf<String>()

        override fun invoke(
            content: String,
            scriptId: String,
            sender: CommandSender?,
            variables: Map<String, Any?>
        ): Any? {
            invoked += Invocation(content = content, scriptId = scriptId)
            return invokeResult ?: scriptId
        }

        override fun preheat(content: String, scriptId: String) {
            preheated += Invocation(content = content, scriptId = scriptId)
        }

        override fun invalidate(id: String) {
            invalidated += id
        }

        override fun invalidateByPrefix(prefix: String) {
            invalidatedByPrefix += prefix
        }

        override fun cacheStats(): ScriptCacheStats {
            return stats
        }
    }

    private data class Invocation(
        val content: String,
        val scriptId: String
    )
}
