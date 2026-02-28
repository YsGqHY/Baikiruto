package org.tabooproject.baikiruto.core.item

import com.google.gson.JsonObject
import org.bukkit.inventory.ItemStack

interface ItemSerializer {

    fun serialize(itemStack: ItemStack): SerializedItem

    fun serialize(itemStream: ItemStream): SerializedItem

    fun deserialize(json: String): ItemStream

    fun deserialize(json: JsonObject): ItemStream

    fun deserialize(serializedItem: SerializedItem): ItemStream
}
