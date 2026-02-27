package org.tabooproject.baikiruto.core.item

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

    fun build(context: Map<String, Any?> = emptyMap()): ItemStream

    fun drop(stream: ItemStream, context: Map<String, Any?> = emptyMap()) {
        metas.reversed().forEach { it.drop(stream) }
    }

    fun collectScripts(): Map<String, String> {
        val values = linkedMapOf<String, String>()
        values += scripts.toScriptMap(id)
        metas.forEach { meta ->
            values += meta.scripts.toScriptMap("$id:meta:${meta.id}")
        }
        return values
    }
}
