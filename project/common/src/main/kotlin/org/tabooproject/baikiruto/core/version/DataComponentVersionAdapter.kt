package org.tabooproject.baikiruto.core.version

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * 1.20.5+ Data Component API 适配器
 * 使用新版组件系统替代旧版 ItemMeta API
 */
open class DataComponentVersionAdapter : BaseItemMetaVersionAdapter() {

    override fun applyDisplayName(itemStack: ItemStack, displayName: String?) {
        if (displayName.isNullOrBlank()) {
            return
        }
        val itemMeta = itemStack.itemMeta ?: return

        // 尝试使用 setItemName (1.20.5+)
        val setItemName = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setItemName" && method.parameterCount == 1
        }

        if (setItemName != null) {
            // 使用新版 item_name 组件
            val component = createLegacyTextComponent(displayName)
            if (component != null && runCatching { setItemName.invoke(itemMeta, component) }.isSuccess) {
                itemStack.itemMeta = itemMeta
                return
            }
        }

        // 降级到旧版 API
        super.applyDisplayName(itemStack, displayName)
    }

    override fun applyLore(itemStack: ItemStack, lore: List<String>) {
        if (lore.isEmpty()) {
            return
        }
        val itemMeta = itemStack.itemMeta ?: return

        // 尝试使用 setLore with Component (1.20.5+)
        val setLore = itemMeta.javaClass.methods.firstOrNull { method ->
            method.name == "setLore" && method.parameterCount == 1
        }

        if (setLore != null) {
            val components = lore.mapNotNull { createLegacyTextComponent(it) }
            if (components.size == lore.size && runCatching { setLore.invoke(itemMeta, components) }.isSuccess) {
                itemStack.itemMeta = itemMeta
                return
            }
        }

        // 降级到旧版 API
        super.applyLore(itemStack, lore)
    }

    /**
     * 创建 Adventure Component (1.20.5+)
     * 使用 LegacyComponentSerializer 解析 & 颜色代码
     */
    private fun createLegacyTextComponent(text: String): Any? {
        // 尝试 net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
        val serializerClass = runCatching {
            Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")
        }.getOrNull() ?: return null

        // 获取 legacyAmpersand() 实例
        val legacyAmpersand = serializerClass.methods.firstOrNull { method ->
            method.name == "legacyAmpersand" && method.parameterCount == 0
        }?.let { method ->
            runCatching { method.invoke(null) }.getOrNull()
        } ?: return null

        // 调用 deserialize(text)
        val deserialize = serializerClass.methods.firstOrNull { method ->
            method.name == "deserialize" && method.parameterCount == 1 && method.parameterTypes[0] == String::class.java
        } ?: return null

        return runCatching { deserialize.invoke(legacyAmpersand, text) }.getOrNull()
    }
}
