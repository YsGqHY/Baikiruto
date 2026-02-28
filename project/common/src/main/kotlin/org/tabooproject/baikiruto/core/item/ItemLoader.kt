package org.tabooproject.baikiruto.core.item

interface ItemLoader {

    fun reloadItems(source: String = "api-reload"): Int

    fun loadedIds(): Set<String>
}
