package org.tabooproject.baikiruto.core.item.event

import taboolib.platform.type.BukkitProxyEvent

sealed class PluginReloadEvent(
    val source: String
) : BukkitProxyEvent() {

    override val allowCancelled: Boolean
        get() = false

    class Item(
        source: String,
        val loadedItems: Int,
        val loadedModels: Int,
        val loadedGroups: Int
    ) : PluginReloadEvent(source)

    class Display(
        source: String,
        val loadedDisplays: Int
    ) : PluginReloadEvent(source)
}

