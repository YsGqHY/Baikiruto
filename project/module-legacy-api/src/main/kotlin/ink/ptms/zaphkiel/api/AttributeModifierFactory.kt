package ink.ptms.zaphkiel.api

import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlot
import org.tabooproject.baikiruto.core.item.Attributes as CoreAttributes

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
    var factory: AttributeModifierFactory = object : AttributeModifierFactory {
        override fun create(
            name: String,
            amount: Double,
            operation: AttributeModifier.Operation,
            equipmentSlot: EquipmentSlot?
        ): AttributeModifier? {
            return CoreAttributes.createAttributeModifier(name, amount, operation, equipmentSlot)
        }
    }

    fun createAttributeModifier(
        name: String,
        amount: Double,
        operation: AttributeModifier.Operation,
        equipmentSlot: EquipmentSlot?
    ): AttributeModifier? {
        val modifier = factory.create(name, amount, operation, equipmentSlot)
        if (modifier != null) {
            return modifier
        }
        return CoreAttributes.createAttributeModifier(name, amount, operation, equipmentSlot)
    }
}
