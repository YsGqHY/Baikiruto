package org.tabooproject.baikiruto.impl.script.handler

import org.bukkit.command.CommandSender
import org.tabooproject.baikiruto.core.ScriptCacheStats

/**
 * Aiyatsbus
 * cc.polarastrum.aiyatsbus.module.script.fluxon.handler.FluxonHandler
 *
 * @author mical
 * @since 2026/1/3 16:19
 */
interface FluxonHandler {

    fun invoke(
        source: String,
        id: String,
        sender: CommandSender?,
        variables: Map<String, Any?>
    ): Any?

    fun preheat(source: String, id: String)

    fun invalidate(id: String)

    fun invalidateByPrefix(prefix: String)

    fun cacheStats(): ScriptCacheStats
}
