package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.item.feature.ItemUniqueFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemUniqueFeatureTest {

    @Test
    fun `should expose custom deny message only when configured`() {
        val configured = DefaultItemStream(
            backingItem = ItemStack(Material.STONE),
            itemId = "test:item",
            versionHash = "v1",
            initialRuntimeData = linkedMapOf(
                "unique-deny-message" to "&cDenied"
            )
        )
        val blank = DefaultItemStream(
            backingItem = ItemStack(Material.STONE),
            itemId = "test:item",
            versionHash = "v1",
            initialRuntimeData = linkedMapOf(
                "unique-deny-message" to "   "
            )
        )

        assertEquals("&cDenied", ItemUniqueFeature.customDenyMessage(configured))
        assertNull(ItemUniqueFeature.customDenyMessage(blank))
    }
}
