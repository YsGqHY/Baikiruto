package org.tabooproject.baikiruto.core.item

import org.bukkit.Bukkit
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import java.lang.reflect.Constructor
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
                    createModifier(
                        "UUID+slot constructor",
                        UUID.randomUUID(),
                        name,
                        amount,
                        operation,
                        equipmentSlot
                    )?.let { return it }
                    createModifier(
                        "name+amount+operation+slot constructor",
                        name,
                        amount,
                        operation,
                        equipmentSlot
                    )?.let { return it }
                }
                createModifier(
                    "UUID constructor (no slot)",
                    UUID.randomUUID(),
                    name,
                    amount,
                    operation
                )?.let { return it }
                createModifier(
                    "name+amount+operation constructor",
                    name,
                    amount,
                    operation
                )?.let { return it }
                debugLog("[Baikiruto/Debug] defaultFactory: all constructors failed, returning null")
                return null
            }

            private fun createModifier(label: String, vararg args: Any): AttributeModifier? {
                val constructor = findConstructor(*args)
                if (constructor == null) {
                    debugLog("[Baikiruto/Debug] defaultFactory: $label unavailable on ${AttributeModifier::class.java.name}")
                    return null
                }
                return try {
                    constructor.newInstance(*args) as? AttributeModifier
                } catch (ex: ReflectiveOperationException) {
                    debugLog("[Baikiruto/Debug] defaultFactory: $label failed: ${ex.message}")
                    null
                } catch (ex: IllegalArgumentException) {
                    debugLog("[Baikiruto/Debug] defaultFactory: $label failed: ${ex.message}")
                    null
                }
            }

            private fun findConstructor(vararg args: Any): Constructor<*>? {
                return AttributeModifier::class.java.constructors.firstOrNull { constructor ->
                    constructor.parameterCount == args.size &&
                        constructor.parameterTypes.indices.all { index ->
                            isParameterCompatible(constructor.parameterTypes[index], args[index])
                        }
                }
            }

            private fun isParameterCompatible(parameterType: Class<*>, value: Any?): Boolean {
                if (value == null) {
                    return !parameterType.isPrimitive
                }
                if (parameterType.isInstance(value)) {
                    return true
                }
                if (!parameterType.isPrimitive) {
                    return parameterType.isAssignableFrom(value.javaClass)
                }
                val wrapper = when (parameterType) {
                    java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
                    java.lang.Byte.TYPE -> java.lang.Byte::class.java
                    java.lang.Short.TYPE -> java.lang.Short::class.java
                    java.lang.Integer.TYPE -> java.lang.Integer::class.java
                    java.lang.Long.TYPE -> java.lang.Long::class.java
                    java.lang.Float.TYPE -> java.lang.Float::class.java
                    java.lang.Double.TYPE -> java.lang.Double::class.java
                    java.lang.Character.TYPE -> java.lang.Character::class.java
                    else -> null
                }
                return wrapper?.isInstance(value) == true
            }
        }
    }
}
