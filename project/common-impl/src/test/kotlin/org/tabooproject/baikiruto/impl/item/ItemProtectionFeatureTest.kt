package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.item.feature.ItemProtectionFeature
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemProtectionFeatureTest {

    @Test
    fun `should block vanilla and any crafting`() {
        val stream = stream(
            mapOf(
                ItemProtectionFeature.KEY_CRAFTING_VANILLA to true,
                ItemProtectionFeature.KEY_CRAFTING_ANY to true
            )
        )

        assertTrue(ItemProtectionFeature.blocksVanillaCrafting(stream))
        assertTrue(ItemProtectionFeature.blocksAnyCrafting(stream))
        assertTrue(ItemProtectionFeature.blocksStation(stream, "ANVIL"))
    }

    @Test
    fun `should normalize station aliases`() {
        val stream = stream(
            mapOf(
                ItemProtectionFeature.KEY_CRAFTING_STATIONS to listOf("stone_cutter", "附魔台", "smithing_table")
            )
        )

        assertTrue(ItemProtectionFeature.blocksStation(stream, "STONECUTTER"))
        assertTrue(ItemProtectionFeature.blocksStation(stream, "ENCHANTING"))
        assertTrue(ItemProtectionFeature.blocksStation(stream, "SMITHING"))
        assertFalse(ItemProtectionFeature.blocksStation(stream, "GRINDSTONE"))
    }

    @Test
    fun `should normalize container aliases`() {
        val stream = stream(
            mapOf(
                ItemProtectionFeature.KEY_CONTAINERS_DENY to listOf("decorated_pot", "熔炉", "盔甲架")
            )
        )

        assertTrue(ItemProtectionFeature.blocksContainer(stream, "DECORATED_POT"))
        assertTrue(ItemProtectionFeature.blocksContainer(stream, "FURNACE"))
        assertTrue(ItemProtectionFeature.blocksContainer(stream, "ARMOR_STAND"))
        assertFalse(ItemProtectionFeature.blocksContainer(stream, "HOPPER"))
    }

    @Test
    fun `should match destroy damage cause aliases`() {
        val stream = stream(
            mapOf(
                ItemProtectionFeature.KEY_DESTROY_ENABLED to true,
                ItemProtectionFeature.KEY_DESTROY_CAUSES to listOf("fire", "lava", "cactus", "lightning", "explosion", "void")
            )
        )

        assertTrue(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.FIRE_TICK))
        assertTrue(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.HOT_FLOOR))
        assertTrue(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.LAVA))
        assertTrue(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.CONTACT))
        assertTrue(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.LIGHTNING))
        assertTrue(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION))
        assertTrue(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.VOID))
    }

    @Test
    fun `should ignore missing protection data`() {
        val stream = stream(emptyMap())

        assertFalse(ItemProtectionFeature.blocksVanillaCrafting(stream))
        assertFalse(ItemProtectionFeature.blocksAnyCrafting(stream))
        assertFalse(ItemProtectionFeature.blocksStation(stream, "ANVIL"))
        assertFalse(ItemProtectionFeature.blocksContainer(stream, "HOPPER"))
        assertFalse(ItemProtectionFeature.isDestroyProtected(stream, EntityDamageEvent.DamageCause.FIRE))
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
