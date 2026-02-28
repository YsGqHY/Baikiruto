package org.tabooproject.baikiruto.module.modern

import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.tabooproject.baikiruto.core.item.AttributeModifierFactory
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
            val anyGroup = runCatching {
                val groupClass = Class.forName("org.bukkit.inventory.EquipmentSlotGroup")
                groupClass.getField("ANY").get(null)
            }.getOrNull()
            if (anyGroup != null) {
                runCatching {
                    val ctor = AttributeModifier::class.java.constructors.firstOrNull { ctor ->
                        ctor.parameterCount == 4 &&
                            ctor.parameterTypes[0] == NamespacedKey::class.java &&
                            ctor.parameterTypes[1] == java.lang.Double.TYPE &&
                            ctor.parameterTypes[2] == AttributeModifier.Operation::class.java
                    } ?: return@runCatching null
                    return ctor.newInstance(key, amount, operation, anyGroup) as? AttributeModifier
                }
            }
        }
        if (equipmentSlot != null) {
            runCatching {
                return AttributeModifier(UUID.randomUUID(), name, amount, operation, equipmentSlot)
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
            return AttributeModifier(UUID.randomUUID(), name, amount, operation)
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
