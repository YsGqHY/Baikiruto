package org.tabooproject.baikiruto.impl.script

import org.bukkit.command.CommandSender
import org.tabooproject.baikiruto.core.BaikirutoScriptHandler
import org.tabooproject.baikiruto.core.BaikirutoScriptSource
import org.tabooproject.baikiruto.core.BaikirutoScriptType
import org.tabooproject.baikiruto.core.ScriptCacheStats
import org.tabooproject.baikiruto.core.item.Registry
import org.tabooproject.baikiruto.impl.item.registry.ConcurrentRegistry
import org.tabooproject.baikiruto.impl.script.handler.Fluxon
import org.tabooproject.baikiruto.impl.script.handler.FluxonHandler
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.PlatformFactory
import taboolib.common.platform.function.console
import taboolib.module.lang.sendLang
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.FluxonScriptHandler
 *
 * @author mical
 * @since 2025/6/22 13:24
 */
class DefaultScriptHandler : BaikirutoScriptHandler {

    private val scriptTypeRegistry = ConcurrentRegistry<BaikirutoScriptType>()

    init {
        registerBuiltinScriptTypes()
    }

    override fun invoke(
        source: BaikirutoScriptSource,
        id: String,
        sender: CommandSender?,
        variables: Map<String, Any?>
    ): Any? {
        val scriptType = resolveScriptType(source, id) ?: return null
        return scriptType.invoke(source.content, id, sender, variables)
    }

    override fun preheat(source: BaikirutoScriptSource, id: String) {
        val scriptType = resolveScriptType(source, id) ?: return
        scriptType.preheat(source.content, id)
    }

    override fun registerScriptType(scriptType: BaikirutoScriptType): BaikirutoScriptType {
        scriptTypeRegistry.register(scriptType.id, scriptType)
        sendLangSafely("log-script-type-registered", BaikirutoScriptSource.normalizeType(scriptType.id))
        return scriptType
    }

    override fun unregisterScriptType(scriptTypeId: String): BaikirutoScriptType? {
        val removed = scriptTypeRegistry.unregister(scriptTypeId)
        if (removed != null) {
            sendLangSafely("log-script-type-unregistered", BaikirutoScriptSource.normalizeType(removed.id))
        }
        return removed
    }

    override fun getScriptType(scriptTypeId: String): BaikirutoScriptType? {
        return scriptTypeRegistry.get(scriptTypeId)
    }

    override fun getScriptTypeRegistry(): Registry<BaikirutoScriptType> {
        return scriptTypeRegistry
    }

    override fun invalidate(id: String) {
        scriptTypeRegistry.values().forEach { scriptType ->
            scriptType.invalidate(id)
        }
    }

    override fun invalidateByPrefix(prefix: String) {
        scriptTypeRegistry.values().forEach { scriptType ->
            scriptType.invalidateByPrefix(prefix)
        }
    }

    override fun cacheStats(): ScriptCacheStats {
        return scriptTypeRegistry.values()
            .map { scriptType -> scriptType.cacheStats() }
            .fold(ScriptCacheStats()) { acc, stats ->
                ScriptCacheStats(
                    cacheSize = acc.cacheSize + stats.cacheSize,
                    invokeHits = acc.invokeHits + stats.invokeHits,
                    invokeMisses = acc.invokeMisses + stats.invokeMisses,
                    totalCompilations = acc.totalCompilations + stats.totalCompilations,
                    totalCompilationNanos = acc.totalCompilationNanos + stats.totalCompilationNanos
                )
            }
    }

    private fun registerBuiltinScriptTypes() {
        scriptTypeRegistry.register(FluxonScriptType.id, FluxonScriptType)
    }

    private fun resolveScriptType(source: BaikirutoScriptSource, scriptId: String): BaikirutoScriptType? {
        val typeId = source.normalizedType()
        val scriptType = scriptTypeRegistry.get(typeId)
        if (scriptType == null) {
            sendLangSafely("log-script-type-missing", typeId, scriptId)
        }
        return scriptType
    }

    private fun sendLangSafely(key: String, vararg args: Any) {
        runCatching {
            console().sendLang(key, *args)
        }
    }

    private object FluxonScriptType : BaikirutoScriptType {

        override val id: String = BaikirutoScriptSource.DEFAULT_TYPE

        override fun invoke(
            content: String,
            scriptId: String,
            sender: CommandSender?,
            variables: Map<String, Any?>
        ): Any? {
            return resolveFluxonHandler().invoke(content, scriptId, sender, variables)
        }

        override fun preheat(content: String, scriptId: String) {
            resolveFluxonHandler().preheat(content, scriptId)
        }

        override fun invalidate(id: String) {
            runCatching {
                resolveFluxonHandler().invalidate(id)
            }
        }

        override fun invalidateByPrefix(prefix: String) {
            runCatching {
                resolveFluxonHandler().invalidateByPrefix(prefix)
            }
        }

        override fun cacheStats(): ScriptCacheStats {
            return runCatching {
                resolveFluxonHandler().cacheStats()
            }.getOrDefault(ScriptCacheStats())
        }
    }

    companion object {

        val DEFAULT_PACKAGE_AUTO_IMPORT = mutableSetOf<String>()
        private val registered = AtomicBoolean(false)

        lateinit var fluxonHandler: FluxonHandler

        private object UnavailableFluxonHandler : FluxonHandler {

            override fun invoke(
                source: String,
                id: String,
                sender: CommandSender?,
                variables: Map<String, Any?>
            ): Any? {
                return null
            }

            override fun preheat(source: String, id: String) {
            }

            override fun invalidate(id: String) {
            }

            override fun invalidateByPrefix(prefix: String) {
            }

            override fun cacheStats(): ScriptCacheStats {
                return ScriptCacheStats()
            }
        }

        fun resolveFluxonHandler(): FluxonHandler {
            return if (::fluxonHandler.isInitialized) {
                fluxonHandler
            } else if (FluxonChecker.isReady()) {
                Fluxon
            } else {
                UnavailableFluxonHandler
            }
        }

        @Awake(LifeCycle.LOAD)
        fun init() {
            if (registered.compareAndSet(false, true)) {
                val handler = DefaultScriptHandler()
                PlatformFactory.registerAPI<BaikirutoScriptHandler>(handler)
                handler.sendLangSafely(
                    "log-script-handler-registered",
                    handler.getScriptTypeRegistry().keys().sorted().joinToString(",")
                )
            }
        }
    }
}
