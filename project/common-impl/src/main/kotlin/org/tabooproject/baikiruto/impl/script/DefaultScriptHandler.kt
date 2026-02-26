package org.tabooproject.baikiruto.impl.script

import org.bukkit.command.CommandSender
import org.tabooproject.baikiruto.core.BaikirutoScriptHandler
import org.tabooproject.baikiruto.impl.script.handler.FluxonHandler
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.PlatformFactory

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.FluxonScriptHandler
 *
 * @author mical
 * @since 2025/6/22 13:24
 */
class DefaultScriptHandler : BaikirutoScriptHandler {

    override fun invoke(source: String, id: String, sender: CommandSender?, variables: Map<String, Any?>): Any? {
        return fluxonHandler.invoke(source, id, sender, variables)
    }

    override fun preheat(source: String, id: String) {
        return fluxonHandler.preheat(source, id)
    }

    companion object {

        val DEFAULT_PACKAGE_AUTO_IMPORT = mutableSetOf<String>()

        lateinit var fluxonHandler: FluxonHandler

        @Awake(LifeCycle.INIT)
        fun init() {
            PlatformFactory.registerAPI<BaikirutoScriptHandler>(DefaultScriptHandler())
        }
    }
}