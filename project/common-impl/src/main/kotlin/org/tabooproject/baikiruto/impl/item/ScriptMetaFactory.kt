package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.ItemScriptHooks
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.MetaFactory

object ScriptMetaFactory : MetaFactory {

    override val id: String = "script"

    override fun create(metaId: String, source: Map<String, Any?>, scripts: ItemScriptHooks): Meta {
        return DefaultMeta(
            id = metaId,
            scripts = scripts
        )
    }
}
