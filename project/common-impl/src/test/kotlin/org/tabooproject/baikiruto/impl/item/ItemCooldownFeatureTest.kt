package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.impl.item.feature.ItemCooldownFeature
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemCooldownFeatureTest {

    @Test
    fun `should apply cancelled cooldown only for configured triggers`() {
        val stream = stream(
            mapOf(
                ItemCooldownFeature.KEY_APPLY_ON_CANCELLED_TRIGGERS to listOf("on_shoot", "right_click")
            )
        )

        assertTrue(ItemCooldownFeature.shouldApplyOnCancelled(stream, listOf(ItemScriptTrigger.SHOOT)))
        assertTrue(ItemCooldownFeature.shouldApplyOnCancelled(stream, listOf(ItemScriptTrigger.INTERACT, ItemScriptTrigger.RIGHT_CLICK)))
        assertFalse(ItemCooldownFeature.shouldApplyOnCancelled(stream, listOf(ItemScriptTrigger.USE)))
    }

    @Test
    fun `should ignore cancelled cooldown when triggers are not configured`() {
        val stream = stream(emptyMap())

        assertFalse(ItemCooldownFeature.shouldApplyOnCancelled(stream, listOf(ItemScriptTrigger.SHOOT)))
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
