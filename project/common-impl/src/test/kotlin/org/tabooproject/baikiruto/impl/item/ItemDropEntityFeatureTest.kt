package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.item.feature.ItemDropEntityFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemDropEntityFeatureTest {

    @Test
    fun `should resolve colored drop display name`() {
        val stream = stream(
            mapOf(
                ItemDropEntityFeature.KEY_DISPLAY_NAME to "&6Protected Artifact",
                ItemDropEntityFeature.KEY_DISPLAY_VISIBLE to false
            )
        )

        assertTrue(ItemDropEntityFeature.hasDropDisplay(stream))
        assertEquals("§6Protected Artifact", ItemDropEntityFeature.resolveDisplayName(stream))
        assertFalse(ItemDropEntityFeature.resolveDisplayVisible(stream))
    }

    @Test
    fun `should default drop display visible to true`() {
        val stream = stream(
            mapOf(ItemDropEntityFeature.KEY_DISPLAY_NAME to "&eDrop")
        )

        assertTrue(ItemDropEntityFeature.resolveDisplayVisible(stream))
    }

    @Test
    fun `should ignore blank drop display name`() {
        val stream = stream(
            mapOf(ItemDropEntityFeature.KEY_DISPLAY_NAME to "   ")
        )

        assertFalse(ItemDropEntityFeature.hasDropDisplay(stream))
        assertNull(ItemDropEntityFeature.resolveDisplayName(stream))
    }

    private fun stream(data: Map<String, Any?>): DefaultItemStream {
        return DefaultItemStream(
            backingItem = ItemStack(Material.STONE),
            itemId = "test:item",
            versionHash = "v1",
            initialRuntimeData = data
        )
    }
}
