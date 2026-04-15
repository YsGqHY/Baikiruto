package org.tabooproject.baikiruto.module.modern

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.tabooproject.baikiruto.core.item.AttributeModifierFactory
import java.lang.reflect.Constructor
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

        val keyString = name.replace('.', '/').lowercase()
        val key = NamespacedKey.fromString("baikiruto:$keyString")
        if (key != null) {
            if (equipmentSlot != null) {
                createModifier(
                    "NamespacedKey+slotGroup constructor",
                    key,
                    amount,
                    operation,
                    equipmentSlot.group
                )?.let { return it }
            }
            createModifier(
                "NamespacedKey+ANY constructor",
                key,
                amount,
                operation,
                EquipmentSlotGroup.ANY
            )?.let { return it }
        } else {
            debugLog("[Baikiruto/Debug] ModernFactory: NamespacedKey.fromString returned null for key '$keyString'")
        }

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

        debugLog("[Baikiruto/Debug] ModernFactory: all constructors failed, returning null")
        return null
    }

    private fun createModifier(label: String, vararg args: Any): AttributeModifier? {
        val constructor = findConstructor(*args)
        if (constructor == null) {
            debugLog("[Baikiruto/Debug] ModernFactory: $label unavailable on ${AttributeModifier::class.java.name}")
            return null
        }
        return try {
            constructor.newInstance(*args) as? AttributeModifier
        } catch (ex: ReflectiveOperationException) {
            debugLog("[Baikiruto/Debug] ModernFactory: $label failed: ${ex.message}")
            null
        } catch (ex: IllegalArgumentException) {
            debugLog("[Baikiruto/Debug] ModernFactory: $label failed: ${ex.message}")
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
