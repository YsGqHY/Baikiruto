package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.item.feature.ItemDurabilityFeature
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemDurabilityFeatureTest {

    @Test
    fun `should sync vanilla damage and max damage components`() {
        val stream = DefaultItemStream(
            backingItem = ItemStack(Material.DIAMOND_SWORD),
            itemId = "test:item",
            versionHash = "v1",
            initialRuntimeData = linkedMapOf(
                "durability" to 200,
                "durability_current" to 50,
                "durability-synchronous" to true,
                "components" to mapOf(
                    "custom_data" to mapOf("foo" to "bar")
                )
            )
        )

        ItemDurabilityFeature.prepare(stream)

        val components = stream.getRuntimeData("components") as Map<*, *>
        val maxDamage = Material.DIAMOND_SWORD.maxDurability.toInt()
        val expectedDamage = (maxDamage.toDouble() * 0.75).roundToInt()

        assertEquals(expectedDamage, stream.getRuntimeData("damage"))
        assertEquals(expectedDamage, components["damage"])
        assertEquals(maxDamage, components["max_damage"])
        assertEquals(mapOf("foo" to "bar"), components["custom_data"])
    }
}
