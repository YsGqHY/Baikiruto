package org.tabooproject.baikiruto.module.modern

import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.tabooproject.baikiruto.core.item.AttributeModifierFactory
import taboolib.library.reflex.Reflex.Companion.invokeConstructor
import java.util.UUID

object AttributeModifierFactoryModern : AttributeModifierFactory {

    override fun create(
        name: String,
        amount: Double,
        operation: AttributeModifier.Operation,
        equipmentSlot: EquipmentSlot?
    ): AttributeModifier? {
        val key = NamespacedKey.fromString("baikiruto:${UUID.randomUUID().toString().replace("-", "")}")
        if (key != null) {
            if (equipmentSlot != null) {
                runCatching {
                    return AttributeModifier(key, amount, operation, equipmentSlot.group)
                }
            }
            runCatching {
                return AttributeModifier::class.java.invokeConstructor(
                    key,
                    amount,
                    operation,
                    EquipmentSlotGroup.ANY
                )
            }
        }
        if (equipmentSlot != null) {
            runCatching {
                return AttributeModifier(UUID.randomUUID(), name, amount, operation, equipmentSlot)
            }
            runCatching {
                return AttributeModifier::class.java.invokeConstructor(
                    name,
                    amount,
                    operation,
                    equipmentSlot
                )
            }
        }
        runCatching {
            return AttributeModifier(UUID.randomUUID(), name, amount, operation)
        }
        runCatching {
            return AttributeModifier::class.java.invokeConstructor(name, amount, operation)
        }
        return null
    }
}
