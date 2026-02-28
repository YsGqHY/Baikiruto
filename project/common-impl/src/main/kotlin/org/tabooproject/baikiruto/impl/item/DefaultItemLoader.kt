package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.ItemLoader
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemModel
import org.tabooproject.baikiruto.core.item.Meta
import taboolib.library.configuration.ConfigurationSection
import java.io.File

object DefaultItemLoader : ItemLoader {

    override fun reloadItems(source: String): Int {
        return ItemDefinitionLoader.reloadItems(source)
    }

    override fun loadedIds(): Set<String> {
        return ItemDefinitionLoader.loadedIds()
    }

    override fun loadItemFromFile(file: File): List<Item> {
        return ItemDefinitionLoader.loadItemFromFile(
            file = file,
            manager = Baikiruto.api().getItemManager()
        )
    }

    override fun loadModelFromFile(file: File): List<ItemModel> {
        return ItemDefinitionLoader.loadModelFromFile(file)
    }

    override fun loadDisplayFromFile(file: File, fromItemFile: Boolean): List<ItemDisplay> {
        return ItemDefinitionLoader.loadDisplayFromFile(file, fromItemFile)
    }

    override fun loadMetaFromSection(root: ConfigurationSection): List<Meta> {
        return ItemDefinitionLoader.loadMetaFromSection(
            root = root,
            manager = Baikiruto.api().getItemManager()
        )
    }
}
