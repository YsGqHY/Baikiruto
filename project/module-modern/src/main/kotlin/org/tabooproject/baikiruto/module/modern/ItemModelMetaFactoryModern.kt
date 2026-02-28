package org.tabooproject.baikiruto.module.modern

import org.tabooproject.baikiruto.core.item.ItemScriptHooks
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.MetaFactory
import org.tabooproject.baikiruto.impl.item.DefaultMeta

object ItemModelMetaFactoryModern : MetaFactory {

    override val id: String = "item-model"

    override fun create(metaId: String, source: Map<String, Any?>, scripts: ItemScriptHooks): Meta {
        val modelId = source["item-model"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: source["model-id"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: source["model"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: source["value"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return DefaultMeta(
            id = metaId,
            scripts = scripts,
            onBuild = { stream ->
                if (!modelId.isNullOrBlank()) {
                    stream.setRuntimeData("item-model", modelId)
                }
            },
            onDrop = { stream ->
                if (!modelId.isNullOrBlank()) {
                    stream.setRuntimeData("item-model", null)
                }
            }
        )
    }
}
