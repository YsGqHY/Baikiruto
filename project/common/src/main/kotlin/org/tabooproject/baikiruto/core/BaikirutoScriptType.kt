package org.tabooproject.baikiruto.core

import org.bukkit.command.CommandSender

interface BaikirutoScriptType {

    val id: String

    fun invoke(
        content: String,
        scriptId: String,
        sender: CommandSender?,
        variables: Map<String, Any?> = emptyMap()
    ): Any?

    fun preheat(content: String, scriptId: String) {
    }

    fun invalidate(id: String) {
    }

    fun invalidateByPrefix(prefix: String) {
    }

    fun cacheStats(): ScriptCacheStats {
        return ScriptCacheStats()
    }
}
