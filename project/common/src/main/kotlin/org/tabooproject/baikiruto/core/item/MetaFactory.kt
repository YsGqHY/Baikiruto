package org.tabooproject.baikiruto.core.item

interface MetaFactory {

    val id: String

    fun create(metaId: String, source: Map<String, Any?>, scripts: ItemScriptHooks = ItemScriptHooks()): Meta?
}
