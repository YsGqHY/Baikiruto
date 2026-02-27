package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.ItemScriptHooks
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.Meta

class DefaultMeta(
    override val id: String,
    override val scripts: ItemScriptHooks = ItemScriptHooks(),
    private val onBuild: (ItemStream) -> Unit = {},
    private val onDrop: (ItemStream) -> Unit = {}
) : Meta {

    override fun build(stream: ItemStream) {
        onBuild(stream)
    }

    override fun drop(stream: ItemStream) {
        onDrop(stream)
    }
}
