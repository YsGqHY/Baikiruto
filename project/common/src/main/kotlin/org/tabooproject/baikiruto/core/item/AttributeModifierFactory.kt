package org.tabooproject.baikiruto.core.item

import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import taboolib.library.reflex.Reflex.Companion.invokeConstructor
import java.util.UUID

interface AttributeModifierFactory {

    fun create(
        name: String,
        amount: Double,
        operation: AttributeModifier.Operation,
        equipmentSlot: EquipmentSlot?
    ): AttributeModifier?
}

object Attributes {

    @Volatile
    var factory: AttributeModifierFactory = defaultFactory()

    fun createAttributeModifier(
        name: String,
        amount: Double,
        operation: AttributeModifier.Operation,
        equipmentSlot: EquipmentSlot?
    ): AttributeModifier? {
        return factory.create(name, amount, operation, equipmentSlot)
    }

    fun defaultFactory(): AttributeModifierFactory {
        return object : AttributeModifierFactory {

            override fun create(
                name: String,
                amount: Double,
                operation: AttributeModifier.Operation,
                equipmentSlot: EquipmentSlot?
            ): AttributeModifier? {
                if (equipmentSlot != null) {
                    runCatching {
                        return AttributeModifier(
                            UUID.randomUUID(),
                            name,
                            amount,
                            operation,
                            equipmentSlot
                        )
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
                    return AttributeModifier(
                        UUID.randomUUID(),
                        name,
                        amount,
                        operation
                    )
                }
                runCatching {
                    return AttributeModifier::class.java.invokeConstructor(name, amount, operation)
                }
                return null
            }
        }
    }
}
