package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemSerializer
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemStreamData
import org.tabooproject.baikiruto.core.item.SerializedItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale

object DefaultItemSerializer : ItemSerializer {

    override fun serialize(itemStack: ItemStack): SerializedItem {
        val stream = Baikiruto.api().readItem(itemStack)
        return if (stream == null) {
            DefaultSerializedItem(
                itemId = "minecraft:${itemStack.type.name.lowercase(Locale.ENGLISH)}",
                amount = itemStack.amount,
                versionHash = "vanilla",
                metaHistory = emptyList(),
                runtimeData = emptyMap(),
                itemStackData = encodeItemStack(itemStack)
            )
        } else {
            serialize(stream)
        }
    }

    override fun serialize(itemStream: ItemStream): SerializedItem {
        return DefaultSerializedItem(
            itemId = itemStream.itemId,
            amount = itemStream.itemStack().amount,
            versionHash = itemStream.versionHash,
            metaHistory = itemStream.metaHistory,
            runtimeData = itemStream.runtimeData,
            itemStackData = encodeItemStack(itemStream.snapshot())
        )
    }

    override fun deserialize(serializedItem: SerializedItem): ItemStream {
        val itemStack = decodeItemStack(serializedItem.itemStackData).apply {
            amount = serializedItem.amount.coerceAtLeast(1)
        }
        val payload = ItemStreamData(
            itemId = serializedItem.itemId,
            versionHash = serializedItem.versionHash,
            metaHistory = serializedItem.metaHistory,
            runtimeData = serializedItem.runtimeData
        )
        return ItemStreamTransport.create(itemStack, payload)
    }

    private fun encodeItemStack(itemStack: ItemStack): String {
        val output = ByteArrayOutputStream()
        BukkitObjectOutputStream(output).use { stream ->
            stream.writeObject(itemStack)
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    private fun decodeItemStack(raw: String): ItemStack {
        val bytes = runCatching { Base64.getDecoder().decode(raw) }.getOrNull() ?: return ItemStack(Material.STONE)
        val input = ByteArrayInputStream(bytes)
        return runCatching {
            BukkitObjectInputStream(input).use { stream ->
                (stream.readObject() as? ItemStack) ?: ItemStack(Material.STONE)
            }
        }.getOrDefault(ItemStack(Material.STONE))
    }
}
