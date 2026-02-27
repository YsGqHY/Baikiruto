package org.tabooproject.baikiruto.core

import org.bukkit.command.CommandSender

/**
 * 脚本处理器接口
 *
 * 负责执行脚本。
 * 提供脚本的调用、预热和变量传递功能。
 * 支持异步执行和批量脚本处理。
 *
 * @author mical
 * @since 2025/6/22 13:16
 */
interface BaikirutoScriptHandler {

    /**
     * 执行脚本（字符串版本）
     *
     * 执行单个脚本字符串，支持变量传递和异步执行。
     *
     * @param source 脚本源代码
     * @param id 脚本名称
     * @param sender 命令发送者，用于权限检查和上下文
     * @param variables 传递给脚本的变量映射
     * @return 脚本执行结果
     */
    fun invoke(source: String, id: String, sender: CommandSender?, variables: Map<String, Any?> = emptyMap()): Any?

    /**
     * 预热脚本（字符串版本）
     *
     * 预编译脚本以提高后续执行性能。
     *
     * @param source 脚本源代码
     * @param id 脚本名称
     */
    fun preheat(source: String, id: String)

    fun invalidate(id: String) {
    }

    fun invalidateByPrefix(prefix: String) {
    }

    fun cacheStats(): ScriptCacheStats {
        return ScriptCacheStats()
    }
}
