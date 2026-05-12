package org.tabooproject.baikiruto.impl.script.handler

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.ScriptCacheStats
import org.tabooproject.baikiruto.impl.log.BaikirutoLog
import org.tabooproject.baikiruto.impl.script.relocate.FluxonRelocate
import org.tabooproject.fluxon.FluxonShell
import org.tabooproject.fluxon.FluxonPlugin
import org.tabooproject.fluxon.parser.ParsedScript
import org.tabooproject.fluxon.runtime.Environment
import org.tabooproject.fluxon.runtime.FluxonRuntime
import org.tabooproject.fluxon.runtime.error.FluxonRuntimeError
import org.tabooproject.fluxon.util.exceptFluxonCompletableFutureError
import org.tabooproject.fluxon.util.printError
import taboolib.common.LifeCycle
import taboolib.common.Requires
import taboolib.common.platform.Awake
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Requires(missingClasses = ["!org.tabooproject.fluxon.ParseScript"])
@FluxonRelocate
object Fluxon : FluxonHandler {

    private val parsedScripts = ConcurrentHashMap<String, CachedScript>()
    private val invokeHits = AtomicLong(0)
    private val invokeMisses = AtomicLong(0)
    private val totalCompilations = AtomicLong(0)
    private val totalCompilationNanos = AtomicLong(0)

    private data class CachedScript(
        val source: String,
        val parsed: ParsedScript
    )

    init {
        FluxonPlugin.DEFAULT_ALLOW_EXECUTE_TASK_ON_NON_SCRIPT_ENV = true
        FluxonRuntime.getInstance().sharingIdentity = "Baikiruto"
    }

    @Awake(LifeCycle.DISABLE)
    private fun cleanup() {
        FluxonRuntime.getInstance().unexportAll()
    }

    override fun invoke(
        source: String,
        id: String,
        sender: CommandSender?,
        variables: Map<String, Any?>
    ): Any? {
        val cached = parsedScripts[id]
        val parsed = if (cached == null || cached.source != source) {
            invokeMisses.incrementAndGet()
            preheat(source, id)
            parsedScripts[id]?.parsed ?: return null
        } else {
            invokeHits.incrementAndGet()
            cached.parsed
        }

        val environment = createEnvironment(sender, variables)

        return try {
            FluxonShell.invoke(parsed, environment)?.also { it.exceptFluxonCompletableFutureError() }
        } catch (ex: FluxonRuntimeError) {
            BaikirutoLog.scriptRuntimeFailed(id, ex)
            ex.printError()
            null
        } catch (ex: Throwable) {
            BaikirutoLog.scriptRuntimeFailed(id, ex)
            ex.printStackTrace()
            null
        }
    }

    override fun preheat(source: String, id: String) {
        val startAt = System.nanoTime()
        try {
            val parsed = FluxonShell.parse(source, FluxonRuntime.getInstance().newEnvironment())
            if (parsed == null) {
                BaikirutoLog.scriptCompileFailed(
                    id,
                    IllegalArgumentException("FluxonShell returned null parsed script.")
                )
                return
            }
            parsedScripts[id] = CachedScript(source, parsed)
            totalCompilations.incrementAndGet()
            totalCompilationNanos.addAndGet(System.nanoTime() - startAt)
        } catch (ex: Throwable) {
            BaikirutoLog.scriptCompileFailed(id, ex)
            ex.printStackTrace()
        }
    }

    override fun invalidate(id: String) {
        parsedScripts.remove(id)
    }

    override fun invalidateByPrefix(prefix: String) {
        parsedScripts.keys
            .filter { it.startsWith(prefix) }
            .forEach { parsedScripts.remove(it) }
    }

    override fun cacheStats(): ScriptCacheStats {
        return ScriptCacheStats(
            cacheSize = parsedScripts.size,
            invokeHits = invokeHits.get(),
            invokeMisses = invokeMisses.get(),
            totalCompilations = totalCompilations.get(),
            totalCompilationNanos = totalCompilationNanos.get()
        )
    }

    private fun createEnvironment(sender: CommandSender?, variables: Map<String, Any?>): Environment {
        return FluxonRuntime.getInstance().newEnvironment().also { environment ->
            variables.forEach { (key, value) -> environment.defineRootVariable(key, value) }
            environment.defineRootVariable("sender", sender)
            if (sender is Player) {
                environment.defineRootVariable("player", sender)
            }
        }
    }
}
