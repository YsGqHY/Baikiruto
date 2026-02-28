package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.ItemLoader

object DefaultItemLoader : ItemLoader {

    override fun reloadItems(source: String): Int {
        return ItemDefinitionLoader.reloadItems(source)
    }

    override fun loadedIds(): Set<String> {
        return ItemDefinitionLoader.loadedIds()
    }
}
