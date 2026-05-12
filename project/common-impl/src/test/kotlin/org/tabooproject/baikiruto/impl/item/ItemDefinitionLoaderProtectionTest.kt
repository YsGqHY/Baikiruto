package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.impl.item.feature.ItemDropEntityFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemProtectionFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemDefinitionLoaderProtectionTest {

    @Test
    fun `should parse drop and protection meta into runtime data`() {
        val parsed = invokeParseMetaEffects(
            mapOf(
                "drop" to mapOf(
                    "display-name" to "&6Protected Artifact",
                    "display-visible" to true
                ),
                "protection" to mapOf(
                    "crafting" to mapOf(
                        "vanilla" to true,
                        "any" to true,
                        "stations" to listOf("stone_cutter", "附魔台", "SMITHING")
                    ),
                    "containers" to mapOf(
                        "deny" to listOf("hopper", "熔炉", "ARMOR_STAND")
                    ),
                    "destroy" to mapOf(
                        "enabled" to true,
                        "causes" to listOf("fire", "LAVA", "CONTACT", "lightning")
                    )
                )
            )
        )

        assertEquals("&6Protected Artifact", parsed[ItemDropEntityFeature.KEY_DISPLAY_NAME])
        assertEquals(true, parsed[ItemDropEntityFeature.KEY_DISPLAY_VISIBLE])
        assertEquals(true, parsed[ItemProtectionFeature.KEY_CRAFTING_VANILLA])
        assertEquals(true, parsed[ItemProtectionFeature.KEY_CRAFTING_ANY])
        assertEquals(listOf("STONECUTTER", "ENCHANTING", "SMITHING"), parsed[ItemProtectionFeature.KEY_CRAFTING_STATIONS])
        assertEquals(listOf("HOPPER", "FURNACE", "ARMOR_STAND"), parsed[ItemProtectionFeature.KEY_CONTAINERS_DENY])
        assertEquals(true, parsed[ItemProtectionFeature.KEY_DESTROY_ENABLED])
        assertEquals(listOf("fire", "lava", "cactus", "lightning"), parsed[ItemProtectionFeature.KEY_DESTROY_CAUSES])
    }

    @Test
    fun `should parse alias and shorthand protection fields`() {
        val parsed = invokeParseMetaEffects(
            mapOf(
                "drop_name" to "&eDrop Name",
                "dropVisible" to false,
                "no-craft" to true,
                "noDestroy" to true,
                "rules" to mapOf(
                    "containers" to mapOf(
                        "deny" to "decorated_pot, smoker"
                    )
                ),
                "protect" to mapOf(
                    "crafting" to mapOf(
                        "workStations" to listOf("grindstone", "锻造台")
                    )
                ),
                "protection" to mapOf(
                    "damage" to mapOf(
                        "enabled" to true,
                        "types" to listOf("explosion", "void")
                    )
                )
            )
        )

        assertEquals("&eDrop Name", parsed[ItemDropEntityFeature.KEY_DISPLAY_NAME])
        assertEquals(false, parsed[ItemDropEntityFeature.KEY_DISPLAY_VISIBLE])
        assertEquals(true, parsed[ItemProtectionFeature.KEY_CRAFTING_ANY])
        assertEquals(listOf("GRINDSTONE", "SMITHING"), parsed[ItemProtectionFeature.KEY_CRAFTING_STATIONS])
        assertEquals(listOf("DECORATED_POT", "SMOKER"), parsed[ItemProtectionFeature.KEY_CONTAINERS_DENY])
        assertEquals(true, parsed[ItemProtectionFeature.KEY_DESTROY_ENABLED])
        assertEquals(listOf("explosion", "void"), parsed[ItemProtectionFeature.KEY_DESTROY_CAUSES])
    }

    @Test
    fun `should ignore missing protection data`() {
        val parsed = invokeParseMetaEffects(emptyMap())

        assertTrue(ItemDropEntityFeature.KEY_DISPLAY_NAME !in parsed)
        assertTrue(ItemProtectionFeature.KEY_CRAFTING_ANY !in parsed)
        assertTrue(ItemProtectionFeature.KEY_CONTAINERS_DENY !in parsed)
        assertTrue(ItemProtectionFeature.KEY_DESTROY_ENABLED !in parsed)
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeParseMetaEffects(source: Map<String, Any?>): Map<String, Any?> {
        val method = ItemDefinitionLoader::class.java.getDeclaredMethod("parseMetaEffects", Map::class.java)
        method.isAccessible = true
        return method.invoke(ItemDefinitionLoader, source) as Map<String, Any?>
    }
}
