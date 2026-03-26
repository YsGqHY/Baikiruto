package org.tabooproject.baikiruto.core.item

import org.bukkit.Bukkit
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

    private val debugEnabled: Boolean
        get() = System.getProperty("baikiruto.debug", "false").equals("true", ignoreCase = true)

    private fun debugLog(message: String) {
        if (debugEnabled) {
            Bukkit.getLogger().info(message)
        }
    }

    fun createAttributeModifier(
        name: String,
        amount: Double,
        operation: AttributeModifier.Operation,
        equipmentSlot: EquipmentSlot?
    ): AttributeModifier? {
        debugLog("[Baikiruto/Debug] Attributes.createAttributeModifier: name=$name, amount=$amount, operation=$operation, slot=$equipmentSlot, factory=${factory.javaClass.name}")
        val result = factory.create(name, amount, operation, equipmentSlot)
        debugLog("[Baikiruto/Debug] Attributes.createAttributeModifier: result=$result")
        return result
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
                    }.onFailure {
                        debugLog("[Baikiruto/Debug] defaultFactory: UUID+slot constructor failed: ${it.message}")
                    }
                    runCatching {
                        return AttributeModifier::class.java.invokeConstructor(
                            name,
                            amount,
                            operation,
                            equipmentSlot
                        )
                    }.onFailure {
                        debugLog("[Baikiruto/Debug] defaultFactory: invokeConstructor(name,amount,op,slot) failed: ${it.message}")
                    }
                }
                runCatching {
                    return AttributeModifier(
                        UUID.randomUUID(),
                        name,
                        amount,
                        operation
                    )
                }.onFailure {
                    debugLog("[Baikiruto/Debug] defaultFactory: UUID constructor (no slot) failed: ${it.message}")
                }
                runCatching {
                    return AttributeModifier::class.java.invokeConstructor(name, amount, operation)
                }.onFailure {
                    debugLog("[Baikiruto/Debug] defaultFactory: invokeConstructor(name,amount,op) failed: ${it.message}")
                }
                debugLog("[Baikiruto/Debug] defaultFactory: all constructors failed, returning null")
                return null
            }
        }
    }
}
