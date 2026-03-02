package org.tabooproject.baikiruto.impl.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemBuildDebugMessengerTest {

    @Test
    fun `should format nested map and list as yaml tree lines`() {
        val payload = linkedMapOf<String, Any?>(
            "stream" to linkedMapOf(
                "itemId" to "demo:sword",
                "runtimeData" to linkedMapOf(
                    "name" to linkedMapOf("item_name" to "&6Example"),
                    "lore" to listOf("&7Line 1", "&aLine 2"),
                    "components" to linkedMapOf(
                        "minecraft:custom_data" to linkedMapOf("foo" to "bar")
                    )
                )
            ),
            "itemStack" to linkedMapOf("type" to "DIAMOND_SWORD", "amount" to 1)
        )

        val lines = ItemBuildDebugMessenger.format("baikiruto_debug_item_build", payload)

        assertEquals("baikiruto_debug_item_build:", lines.first())
        assertTrue(lines.contains("  stream:"))
        assertTrue(
            lines.any { line ->
                line.startsWith("    itemId: ") &&
                    (line.endsWith("demo:sword") || line.endsWith("'demo:sword'"))
            }
        )
        assertTrue(
            lines.any { line ->
                line.contains("minecraft:custom_data") && line.trimEnd().endsWith(":")
            }
        )
        assertTrue(lines.contains("  itemStack:"))
    }
}
