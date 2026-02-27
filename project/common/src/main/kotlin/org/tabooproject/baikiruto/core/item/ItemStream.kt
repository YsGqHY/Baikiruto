package org.tabooproject.baikiruto.core.item

import org.bukkit.inventory.ItemStack

interface ItemStream {

    val itemId: String

    val versionHash: String

    val metaHistory: List<String>

    val runtimeData: Map<String, Any?>

    val signals: Set<ItemSignal>

    fun itemStack(): ItemStack

    fun snapshot(): ItemStack

    fun setDisplayName(name: String?): ItemStream

    fun setLore(lines: List<String>): ItemStream

    fun setRuntimeData(key: String, value: Any?): ItemStream

    fun getRuntimeData(key: String): Any?

    fun markSignal(signal: ItemSignal): ItemStream

    fun hasSignal(signal: ItemSignal): Boolean

    fun applyMeta(meta: Meta): ItemStream

    fun snapshotData(): ItemStreamData

    fun toItemStack(): ItemStack
}
