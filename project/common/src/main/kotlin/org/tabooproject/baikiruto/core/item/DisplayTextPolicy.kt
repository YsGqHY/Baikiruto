package org.tabooproject.baikiruto.core.item

/**
 * 控制显示模板层对未知角括号标签的处理策略。
 *
 * 开启后，`<red>` 之类未知标签会被原样保留，交给后续的 MiniMessage 渲染阶段处理。
 */
object DisplayTextPolicy {

    @Volatile
    var preserveUnknownAngleTags: Boolean = false
}
