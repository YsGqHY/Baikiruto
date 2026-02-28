package org.tabooproject.baikiruto.core.item

import taboolib.library.configuration.ConfigurationSection
import java.io.File

interface ItemLoader {

    fun reloadItems(source: String = "api-reload"): Int

    fun loadedIds(): Set<String>

    fun loadItemFromFile(file: File): List<Item>

    fun loadModelFromFile(file: File): List<ItemModel>

    fun loadDisplayFromFile(file: File, fromItemFile: Boolean = false): List<ItemDisplay>

    fun loadMetaFromSection(root: ConfigurationSection): List<Meta>
}
