package org.tabooproject.baikiruto.impl.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ItemAsyncTickPolicyTest {

    @Test
    fun `should resolve interval with fallback and clamp`() {
        assertEquals(100L, ItemAsyncTickPolicy.resolveInterval(100L, null))
        assertEquals(5L, ItemAsyncTickPolicy.resolveInterval(100L, 5))
        assertEquals(3L, ItemAsyncTickPolicy.resolveInterval(100L, "3"))
        assertEquals(100L, ItemAsyncTickPolicy.resolveInterval(100L, 0))
        assertEquals(100L, ItemAsyncTickPolicy.resolveInterval(100L, "oops"))
    }

    @Test
    fun `should resolve enabled flag with sane defaults`() {
        assertTrue(ItemAsyncTickPolicy.resolveEnabled(null))
        assertTrue(ItemAsyncTickPolicy.resolveEnabled(true))
        assertTrue(ItemAsyncTickPolicy.resolveEnabled(1))
        assertTrue(ItemAsyncTickPolicy.resolveEnabled("true"))
        assertFalse(ItemAsyncTickPolicy.resolveEnabled(false))
        assertFalse(ItemAsyncTickPolicy.resolveEnabled(0))
        assertFalse(ItemAsyncTickPolicy.resolveEnabled("0"))
    }

    @Test
    fun `should normalize and resolve async tick slots`() {
        assertEquals(setOf("MAINHAND"), ItemAsyncTickPolicy.resolveConditionSlots("mainhand"))
        assertEquals(setOf("HOTBAR", "OFFHAND"), ItemAsyncTickPolicy.resolveConditionSlots(listOf("hotbar", "offhand")))
        assertEquals("HEAD", ItemAsyncTickPolicy.normalizeSlot("helmet"))
        assertEquals("INVENTORY", ItemAsyncTickPolicy.normalizeSlot("storage"))
    }

    @Test
    fun `should match sneaking and slot conditions`() {
        assertTrue(
            ItemAsyncTickPolicy.matchesConditions(
                conditionSneaking = true,
                conditionSlots = listOf("mainhand"),
                sneaking = true,
                slot = "MAINHAND"
            )
        )
        assertTrue(
            ItemAsyncTickPolicy.matchesConditions(
                conditionSneaking = null,
                conditionSlots = listOf("hotbar"),
                sneaking = false,
                slot = "MAINHAND"
            )
        )
        assertFalse(
            ItemAsyncTickPolicy.matchesConditions(
                conditionSneaking = true,
                conditionSlots = listOf("offhand"),
                sneaking = false,
                slot = "MAINHAND"
            )
        )
        assertFalse(
            ItemAsyncTickPolicy.matchesConditions(
                conditionSneaking = null,
                conditionSlots = listOf("inventory"),
                sneaking = true,
                slot = "HOTBAR"
            )
        )
    }

    @Test
    fun `should resolve world game mode and permission sets`() {
        assertEquals(setOf("world", "world_nether"), ItemAsyncTickPolicy.resolveConditionWorlds("World, world_nether"))
        assertEquals(setOf("SURVIVAL", "ADVENTURE"), ItemAsyncTickPolicy.resolveConditionGameModes(listOf("survival", "adventure")))
        assertEquals(setOf("baikiruto.async.fire"), ItemAsyncTickPolicy.resolveConditionPermissions("baikiruto.async.fire"))
    }

    @Test
    fun `should match extended boolean state conditions`() {
        val conditions = mapOf(
            ItemAsyncTickPolicy.KEY_CONDITION_SPRINTING to true,
            ItemAsyncTickPolicy.KEY_CONDITION_SWIMMING to false,
            ItemAsyncTickPolicy.KEY_CONDITION_ON_GROUND to true,
            ItemAsyncTickPolicy.KEY_CONDITION_BURNING to false
        )

        assertTrue(
            ItemAsyncTickPolicy.matchesConditions(
                conditions = conditions,
                state = conditionState(
                    sprinting = true,
                    swimming = false,
                    onGround = true,
                    burning = false
                )
            )
        )
        assertFalse(
            ItemAsyncTickPolicy.matchesConditions(
                conditions = conditions,
                state = conditionState(
                    sprinting = false,
                    swimming = false,
                    onGround = true,
                    burning = false
                )
            )
        )
        assertFalse(
            ItemAsyncTickPolicy.matchesConditions(
                conditions = conditions,
                state = conditionState(
                    sprinting = true,
                    swimming = false,
                    onGround = true,
                    burning = true
                )
            )
        )
    }

    @Test
    fun `should match world game mode and permission conditions`() {
        val conditions = mapOf(
            ItemAsyncTickPolicy.KEY_CONDITION_WORLDS to "world, world_nether",
            ItemAsyncTickPolicy.KEY_CONDITION_GAME_MODES to listOf("survival"),
            ItemAsyncTickPolicy.KEY_CONDITION_PERMISSIONS to listOf("baikiruto.async.fire", "baikiruto.async.admin")
        )

        assertTrue(
            ItemAsyncTickPolicy.matchesConditions(
                conditions = conditions,
                state = conditionState(
                    world = "World",
                    gameMode = "SURVIVAL",
                    permissions = setOf("baikiruto.async.admin")
                )
            )
        )
        assertFalse(
            ItemAsyncTickPolicy.matchesConditions(
                conditions = conditions,
                state = conditionState(
                    world = "World",
                    gameMode = "SURVIVAL"
                )
            )
        )
        assertFalse(
            ItemAsyncTickPolicy.matchesConditions(
                conditions = conditions,
                state = conditionState(
                    world = "World",
                    gameMode = "CREATIVE",
                    permissions = setOf("baikiruto.async.admin")
                )
            )
        )
        assertFalse(
            ItemAsyncTickPolicy.matchesConditions(
                conditions = conditions,
                state = conditionState(
                    world = "world_the_end",
                    gameMode = "SURVIVAL",
                    permissions = setOf("baikiruto.async.admin")
                )
            )
        )
    }

    @Test
    fun `should spread trigger timing by stable seed`() {
        val interval = 5L
        val seedA = ItemAsyncTickPolicy.stableSeed("player-a", 0, "test:item")
        val seedB = ItemAsyncTickPolicy.stableSeed("player-b", 0, "test:item")

        val ticksA = (1L..20L).filter { ItemAsyncTickPolicy.shouldTrigger(it, interval, seedA) }
        val ticksB = (1L..20L).filter { ItemAsyncTickPolicy.shouldTrigger(it, interval, seedB) }

        assertEquals(4, ticksA.size)
        assertEquals(4, ticksB.size)
        assertNotEquals(ticksA, ticksB)
    }

    @Test
    fun `should always trigger when interval is one`() {
        val seed = ItemAsyncTickPolicy.stableSeed("player-a", 0, "test:item")
        assertTrue((1L..10L).all { ItemAsyncTickPolicy.shouldTrigger(it, 1L, seed) })
    }

    private fun conditionState(
        slot: String = "MAINHAND",
        sneaking: Boolean = false,
        sprinting: Boolean = false,
        swimming: Boolean = false,
        gliding: Boolean = false,
        flying: Boolean = false,
        onGround: Boolean = false,
        inVehicle: Boolean = false,
        burning: Boolean = false,
        blocking: Boolean = false,
        world: String? = null,
        gameMode: String? = null,
        permissions: Set<String> = emptySet()
    ): ItemAsyncTickPolicy.ConditionState {
        return ItemAsyncTickPolicy.ConditionState(
            slot = slot,
            sneaking = sneaking,
            sprinting = sprinting,
            swimming = swimming,
            gliding = gliding,
            flying = flying,
            onGround = onGround,
            inVehicle = inVehicle,
            burning = burning,
            blocking = blocking,
            world = world,
            gameMode = gameMode,
            hasPermission = { permission -> permission in permissions }
        )
    }
}
