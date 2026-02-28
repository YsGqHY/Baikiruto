package org.tabooproject.baikiruto.impl.script

import org.bukkit.command.CommandSender
import org.tabooproject.baikiruto.core.ScriptCacheStats
import org.tabooproject.baikiruto.core.BaikirutoScriptHandler
import org.tabooproject.baikiruto.impl.script.handler.Fluxon
import org.tabooproject.baikiruto.impl.script.handler.FluxonHandler
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.PlatformFactory
import taboolib.common.platform.function.info
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.FluxonScriptHandler
 *
 * @author mical
 * @since 2025/6/22 13:24
 */
class DefaultScriptHandler : BaikirutoScriptHandler {

    override fun invoke(source: String, id: String, sender: CommandSender?, variables: Map<String, Any?>): Any? {
        return resolveFluxonHandler().invoke(source, id, sender, variables)
    }

    override fun preheat(source: String, id: String) {
        return resolveFluxonHandler().preheat(source, id)
    }

    override fun invalidate(id: String) {
        resolveFluxonHandler().invalidate(id)
    }

    override fun invalidateByPrefix(prefix: String) {
        resolveFluxonHandler().invalidateByPrefix(prefix)
    }

    override fun cacheStats(): ScriptCacheStats {
        return resolveFluxonHandler().cacheStats()
    }

    companion object {

        val DEFAULT_PACKAGE_AUTO_IMPORT = mutableSetOf<String>()
        private val registered = AtomicBoolean(false)

        lateinit var fluxonHandler: FluxonHandler

        fun resolveFluxonHandler(): FluxonHandler {
            return if (::fluxonHandler.isInitialized) {
                fluxonHandler
            } else {
                Fluxon
            }
        }

        @Awake(LifeCycle.LOAD)
        fun init() {
            if (registered.compareAndSet(false, true)) {
                PlatformFactory.registerAPI<BaikirutoScriptHandler>(DefaultScriptHandler())
                info("[Baikiruto] Script handler registered in LOAD (engine=Fluxon).")
            }
        }
    }
}
