package org.tabooproject.baikiruto.impl.item

import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.item.feature.ItemUpdateFeature
import java.lang.reflect.Method
import java.util.Locale

object ItemEnchantPreserver {

    fun preserveIfEnabled(source: ItemStack, rebuilt: ItemStack, rebuiltStream: ItemStream): ItemStack {
        if (asBoolean(rebuiltStream.getRuntimeData(ItemUpdateFeature.KEY_PRESERVE_ENCHANTMENTS)) != true) {
            return rebuilt
        }
        val target = rebuilt.clone()
        preserveEnchantments(source, target)
        preserveStoredEnchantments(source, target)
        return target
    }

    private fun preserveEnchantments(source: ItemStack, target: ItemStack) {
        val sourceMeta = source.itemMeta ?: return
        val targetMeta = target.itemMeta ?: return
        val enchants = readEnchantments(sourceMeta, "getEnchants")
        if (enchants.isEmpty()) {
            return
        }
        enchants.forEach { (enchantment, level) ->
            invokeAddEnchant(targetMeta, "addEnchant", enchantment, level)
        }
        target.itemMeta = targetMeta
    }

    private fun preserveStoredEnchantments(source: ItemStack, target: ItemStack) {
        val sourceMeta = source.itemMeta ?: return
        val targetMeta = target.itemMeta ?: return
        val stored = readEnchantments(sourceMeta, "getStoredEnchants")
        if (stored.isEmpty()) {
            return
        }
        stored.forEach { (enchantment, level) ->
            invokeAddEnchant(targetMeta, "addStoredEnchant", enchantment, level)
        }
        target.itemMeta = targetMeta
    }

    private fun readEnchantments(meta: Any, methodName: String): Map<Enchantment, Int> {
        val method = meta.javaClass.methods.firstOrNull { method ->
            method.name == methodName && method.parameterCount == 0
        } ?: return emptyMap()
        val raw = try {
            method.invoke(meta)
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: return emptyMap()
        val source = raw as? Map<*, *> ?: return emptyMap()
        return source.entries.mapNotNull { (key, value) ->
            val enchantment = key as? Enchantment ?: return@mapNotNull null
            val level = (value as? Number)?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
            enchantment to level
        }.toMap(linkedMapOf())
    }

    private fun invokeAddEnchant(meta: Any, methodName: String, enchantment: Enchantment, level: Int): Boolean {
        val method = resolveAddMethod(meta, methodName) ?: return false
        return try {
            method.invoke(meta, enchantment, level, true)
            true
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun resolveAddMethod(meta: Any, methodName: String): Method? {
        return meta.javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterCount == 3 &&
                method.parameterTypes[0].isAssignableFrom(Enchantment::class.java) &&
                method.parameterTypes[1] == Int::class.javaPrimitiveType &&
                method.parameterTypes[2] == Boolean::class.javaPrimitiveType
        }
    }

    private fun asBoolean(source: Any?): Boolean? {
        return when (source) {
            null -> null
            is Boolean -> source
            is Number -> source.toInt() != 0
            is String -> when (source.trim().lowercase(Locale.ENGLISH)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }
}
