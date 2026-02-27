package org.tabooproject.baikiruto.core.item

interface Meta {

    val id: String

    val scripts: ItemScriptHooks
        get() = ItemScriptHooks()

    fun build(stream: ItemStream) {
    }

    fun drop(stream: ItemStream) {
    }
}
