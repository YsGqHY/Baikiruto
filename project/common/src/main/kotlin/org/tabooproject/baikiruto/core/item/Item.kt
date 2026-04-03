package org.tabooproject.baikiruto.core.item

import org.tabooproject.baikiruto.core.BaikirutoScriptSource

interface Item {

    val id: String

    val groupId: String?
        get() = null

    val displayId: String?
        get() = null

    val modelIds: List<String>
        get() = emptyList()

    val metas: List<Meta>

    val scripts: ItemScriptHooks
        get() = ItemScriptHooks()

    val eventData: Map<String, Any?>
        get() = emptyMap()

    fun build(context: Map<String, Any?> = emptyMap()): ItemStream

    fun drop(stream: ItemStream, context: Map<String, Any?> = emptyMap()) {
        metas.reversed().forEach { it.drop(stream) }
    }

    fun collectScriptSources(): Map<String, BaikirutoScriptSource> {
        val values = linkedMapOf<String, BaikirutoScriptSource>()
        values += scripts.toTypedScriptMap(id)
        metas.forEach { meta ->
            values += meta.scripts.toTypedScriptMap("$id:meta:${meta.id}")
        }
        return values
    }

    fun collectScripts(): Map<String, String> {
        return collectScriptSources().mapValues { (_, source) -> source.content }
    }
}
