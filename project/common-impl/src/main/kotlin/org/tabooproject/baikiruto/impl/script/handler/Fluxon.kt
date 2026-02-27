package org.tabooproject.baikiruto.impl.script.handler

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.ScriptCacheStats
import org.tabooproject.baikiruto.impl.log.BaikirutoLog
import org.tabooproject.baikiruto.impl.script.DefaultScriptHandler
import org.tabooproject.baikiruto.impl.script.relocate.FluxonRelocate
import org.tabooproject.fluxon.Fluxon
import org.tabooproject.fluxon.FluxonPlugin
import org.tabooproject.fluxon.compiler.CompilationContext
import org.tabooproject.fluxon.interpreter.bytecode.FluxonClassLoader
import org.tabooproject.fluxon.runtime.FluxonRuntime
import org.tabooproject.fluxon.runtime.RuntimeScriptBase
import org.tabooproject.fluxon.runtime.error.FluxonRuntimeError
import org.tabooproject.fluxon.util.exceptFluxonCompletableFutureError
import org.tabooproject.fluxon.util.printError
import taboolib.common.Requires
import taboolib.platform.BukkitPlugin
import java.text.ParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Requires(missingClasses = ["!org.tabooproject.fluxon.ParseScript"])
@FluxonRelocate
object Fluxon : FluxonHandler {

    private val compiledScripts = ConcurrentHashMap<String, RuntimeScriptBase>()
    private val classLoader = FluxonClassLoader(BukkitPlugin::class.java.classLoader)
    private val environment = FluxonRuntime.getInstance().newEnvironment()
    private val invokeHits = AtomicLong(0)
    private val invokeMisses = AtomicLong(0)
    private val totalCompilations = AtomicLong(0)
    private val totalCompilationNanos = AtomicLong(0)

    init {
        FluxonPlugin.DEFAULT_ALLOW_EXECUTE_TASK_ON_NON_SCRIPT_ENV = true
    }

    override fun invoke(
        source: String,
        id: String,
        sender: CommandSender?,
        variables: Map<String, Any?>
    ): Any? {
        if (!compiledScripts.containsKey(id)) {
            invokeMisses.incrementAndGet()
            preheat(source, id)
        } else {
            invokeHits.incrementAndGet()
        }

        val scriptBase = compiledScripts[id] ?: return null

        val environment = FluxonRuntime.getInstance().newEnvironment()
        variables.forEach { (key, value) -> environment.defineRootVariable(key, value) }
        environment.defineRootVariable("sender", sender)
        if (sender is Player) {
            environment.defineRootVariable("player", sender)
        }

        return try {
            scriptBase.eval(environment)?.exceptFluxonCompletableFutureError()
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
            val result = Fluxon.compile(
                environment,
                CompilationContext(source).apply {
                    packageAutoImport += DefaultScriptHandler.DEFAULT_PACKAGE_AUTO_IMPORT
                },
                id + System.currentTimeMillis(),
                classLoader
            )
            compiledScripts[id] = result.createInstance(classLoader) as RuntimeScriptBase
            totalCompilations.incrementAndGet()
            totalCompilationNanos.addAndGet(System.nanoTime() - startAt)
        } catch (ex: ParseException) {
            BaikirutoLog.scriptCompileFailed(id, ex)
            ex.printStackTrace()
        } catch (ex: Throwable) {
            BaikirutoLog.scriptCompileFailed(id, ex)
            ex.printStackTrace()
        }
    }

    override fun invalidate(id: String) {
        compiledScripts.remove(id)
    }

    override fun invalidateByPrefix(prefix: String) {
        compiledScripts.keys
            .filter { it.startsWith(prefix) }
            .forEach { compiledScripts.remove(it) }
    }

    override fun cacheStats(): ScriptCacheStats {
        return ScriptCacheStats(
            cacheSize = compiledScripts.size,
            invokeHits = invokeHits.get(),
            invokeMisses = invokeMisses.get(),
            totalCompilations = totalCompilations.get(),
            totalCompilationNanos = totalCompilationNanos.get()
        )
    }
}
