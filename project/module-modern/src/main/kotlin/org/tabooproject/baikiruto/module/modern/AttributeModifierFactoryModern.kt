package org.tabooproject.baikiruto.module.modern

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.tabooproject.baikiruto.core.item.AttributeModifierFactory
import taboolib.library.reflex.Reflex.Companion.invokeConstructor
import java.util.UUID

object AttributeModifierFactoryModern : AttributeModifierFactory {

    private val debugEnabled: Boolean
        get() = System.getProperty("baikiruto.debug", "false").equals("true", ignoreCase = true)

    private fun debugLog(message: String) {
        if (debugEnabled) {
            Bukkit.getLogger().info(message)
        }
    }

    override fun create(
        name: String,
        amount: Double,
        operation: AttributeModifier.Operation,
        equipmentSlot: EquipmentSlot?
    ): AttributeModifier? {
        debugLog("[Baikiruto/Debug] ModernFactory.create: name=$name, amount=$amount, operation=$operation, slot=$equipmentSlot")
        // 使用确定性 key：将 name 中的 '.' 替换为 '/'，确保同一 attribute+slot 组合始终使用相同的 key
        val keyString = name.replace('.', '/').lowercase()
        val key = NamespacedKey.fromString("baikiruto:$keyString")
        if (key != null) {
            if (equipmentSlot != null) {
                runCatching {
                    val slotGroup = equipmentSlot.group
                    debugLog("[Baikiruto/Debug] ModernFactory: trying NamespacedKey+slotGroup constructor, slotGroup=$slotGroup")
                    return AttributeModifier(key, amount, operation, slotGroup)
                }.onFailure {
                    debugLog("[Baikiruto/Debug] ModernFactory: NamespacedKey+slotGroup constructor failed: ${it.message}")
                }
            }
            runCatching {
                debugLog("[Baikiruto/Debug] ModernFactory: trying NamespacedKey+ANY constructor")
                return AttributeModifier::class.java.invokeConstructor(
                    key,
                    amount,
                    operation,
                    EquipmentSlotGroup.ANY
                )
            }.onFailure {
                debugLog("[Baikiruto/Debug] ModernFactory: NamespacedKey+ANY constructor failed: ${it.message}")
            }
        } else {
            debugLog("[Baikiruto/Debug] ModernFactory: NamespacedKey.fromString returned null for key '$keyString'")
        }
        if (equipmentSlot != null) {
            runCatching {
                debugLog("[Baikiruto/Debug] ModernFactory: trying UUID+slot constructor")
                return AttributeModifier(UUID.randomUUID(), name, amount, operation, equipmentSlot)
            }.onFailure {
                debugLog("[Baikiruto/Debug] ModernFactory: UUID+slot constructor failed: ${it.message}")
            }
            runCatching {
                debugLog("[Baikiruto/Debug] ModernFactory: trying invokeConstructor(name,amount,op,slot)")
                return AttributeModifier::class.java.invokeConstructor(
                    name,
                    amount,
                    operation,
                    equipmentSlot
                )
            }.onFailure {
                debugLog("[Baikiruto/Debug] ModernFactory: invokeConstructor(name,amount,op,slot) failed: ${it.message}")
            }
        }
        runCatching {
            debugLog("[Baikiruto/Debug] ModernFactory: trying UUID constructor (no slot)")
            return AttributeModifier(UUID.randomUUID(), name, amount, operation)
        }.onFailure {
            debugLog("[Baikiruto/Debug] ModernFactory: UUID constructor (no slot) failed: ${it.message}")
        }
        runCatching {
            debugLog("[Baikiruto/Debug] ModernFactory: trying invokeConstructor(name,amount,op)")
            return AttributeModifier::class.java.invokeConstructor(name, amount, operation)
        }.onFailure {
            debugLog("[Baikiruto/Debug] ModernFactory: invokeConstructor(name,amount,op) failed: ${it.message}")
        }
        debugLog("[Baikiruto/Debug] ModernFactory: all constructors failed, returning null")
        return null
    }
}
