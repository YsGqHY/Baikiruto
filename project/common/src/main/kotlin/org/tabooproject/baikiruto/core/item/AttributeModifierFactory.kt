package org.tabooproject.baikiruto.core.item

import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
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
                        val ctor = AttributeModifier::class.java.constructors.firstOrNull { ctor ->
                            ctor.parameterCount == 4 &&
                                ctor.parameterTypes[0] == String::class.java &&
                                ctor.parameterTypes[1] == java.lang.Double.TYPE &&
                                ctor.parameterTypes[2] == AttributeModifier.Operation::class.java &&
                                ctor.parameterTypes[3] == EquipmentSlot::class.java
                        } ?: return@runCatching null
                        return ctor.newInstance(name, amount, operation, equipmentSlot) as? AttributeModifier
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
                    val ctor = AttributeModifier::class.java.constructors.firstOrNull { ctor ->
                        ctor.parameterCount == 3 &&
                            ctor.parameterTypes[0] == String::class.java &&
                            ctor.parameterTypes[1] == java.lang.Double.TYPE &&
                            ctor.parameterTypes[2] == AttributeModifier.Operation::class.java
                    } ?: return@runCatching null
                    return ctor.newInstance(name, amount, operation) as? AttributeModifier
                }
                return null
            }
        }
    }
}
