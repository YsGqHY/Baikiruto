package org.tabooproject.baikiruto.impl.hook

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.serverct.ersha.api.AttributeAPI
import org.serverct.ersha.api.event.AttrUpdateAttributeEvent
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.platform.util.isAir

object AttributePlusHook {

    private val RANGE_PATTERN = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*[-~:]\s*(-?\d+(?:\.\d+)?)\s*$""")

    @SubscribeEvent(bind = "org.serverct.ersha.api.event.AttrUpdateAttributeEvent\$After")
    fun onAttributeUpdate(event: OptionalEvent) {
        if (!BaikirutoSettings.attributePlusHookEnabled) {
            return
        }
        val source = event.get<AttrUpdateAttributeEvent.After>()
        val sourceEntity = source.attributeData.sourceEntity as? Player ?: return
        val attrData = AttributeAPI.getAttrData(sourceEntity)

        equippedItems(sourceEntity).forEachIndexed { index, item ->
            val sourceKey = "Baikiruto.$index"
            AttributeAPI.takeSourceAttribute(attrData, sourceKey)
            if (item == null || item.isAir()) {
                return@forEachIndexed
            }
            val stream = Baikiruto.api().readItem(item) ?: return@forEachIndexed
            val values = parseAttributePlus(stream.runtimeData) ?: return@forEachIndexed
            AttributeAPI.addSourceAttribute(attrData, sourceKey, values, false)
        }
    }

    private fun equippedItems(player: Player): List<ItemStack?> {
        val equipment = player.equipment
        return listOf(
            equipment?.itemInMainHand,
            equipment?.itemInOffHand,
            equipment?.helmet,
            equipment?.chestplate,
            equipment?.leggings,
            equipment?.boots
        )
    }

    private fun parseAttributePlus(runtimeData: Map<String, Any?>): HashMap<String, Array<Number>>? {
        val raw = runtimeData["attribute-plus"]
            ?: runtimeData["attribute_plus"]
            ?: runtimeData["attributePlus"]
            ?: return null
        val map = raw as? Map<*, *> ?: return null
        val parsed = HashMap<String, Array<Number>>()
        map.forEach { (key, value) ->
            val name = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val range = parseRange(value) ?: return@forEach
            parsed[name] = arrayOf(range.first, range.second)
        }
        return parsed.takeIf { it.isNotEmpty() }
    }

    private fun parseRange(raw: Any?): Pair<Double, Double>? {
        return when (raw) {
            null -> null
            is Number -> {
                val value = raw.toDouble()
                value to value
            }
            is String -> parseRangeFromString(raw)
            is Iterable<*> -> {
                val values = raw.mapNotNull { parseNumber(it) }
                if (values.isEmpty()) {
                    null
                } else {
                    val first = values.first()
                    val second = values.getOrElse(1) { first }
                    first to second
                }
            }
            is Array<*> -> {
                val values = raw.mapNotNull { parseNumber(it) }
                if (values.isEmpty()) {
                    null
                } else {
                    val first = values.first()
                    val second = values.getOrElse(1) { first }
                    first to second
                }
            }
            is Map<*, *> -> {
                val min = parseNumber(raw["min"] ?: raw["from"] ?: raw["start"])
                val max = parseNumber(raw["max"] ?: raw["to"] ?: raw["end"])
                when {
                    min != null && max != null -> min to max
                    min != null -> min to min
                    max != null -> max to max
                    else -> null
                }
            }
            else -> parseNumber(raw)?.let { it to it }
        }
    }

    private fun parseRangeFromString(raw: String): Pair<Double, Double>? {
        val source = raw.trim()
        if (source.isEmpty()) {
            return null
        }
        parseNumber(source)?.let { return it to it }
        val matcher = RANGE_PATTERN.matchEntire(source) ?: return null
        val first = matcher.groupValues[1].toDoubleOrNull()
        val second = matcher.groupValues[2].toDoubleOrNull()
        if (first != null && second != null) {
            return first to second
        }
        return null
    }

    private fun parseNumber(raw: Any?): Double? {
        return when (raw) {
            null -> null
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
            else -> raw.toString().trim().toDoubleOrNull()
        }
    }
}
