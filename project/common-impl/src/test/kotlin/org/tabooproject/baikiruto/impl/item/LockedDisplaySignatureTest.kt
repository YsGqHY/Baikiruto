package org.tabooproject.baikiruto.impl.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LockedDisplaySignatureTest {

    @Test
    fun `should generate signature from locked display runtime`() {
        val runtime = linkedMapOf<String, Any?>(
            "__locked_display_fields__" to listOf("name", "lore"),
            "name" to mapOf("item_name" to "&6Name"),
            "lore" to mapOf("item_description" to listOf("&7Lore"))
        )
        val signature = LockedDisplaySignature.read(runtime)
        assertNotNull(signature)
    }

    @Test
    fun `should change signature when locked display payload changes`() {
        val first = linkedMapOf<String, Any?>(
            "__locked_display_fields__" to listOf("name"),
            "name" to mapOf("item_name" to "&6Name-A")
        )
        val second = linkedMapOf<String, Any?>(
            "__locked_display_fields__" to listOf("name"),
            "name" to mapOf("item_name" to "&6Name-B")
        )
        assertNotEquals(
            LockedDisplaySignature.read(first),
            LockedDisplaySignature.read(second)
        )
    }

    @Test
    fun `should prioritize locked display values payload when available`() {
        val first = linkedMapOf<String, Any?>(
            "__locked_display_fields__" to listOf("name"),
            "name" to mapOf("item_name" to "&bComponent Name"),
            "__locked_display_values__" to mapOf(
                "name" to mapOf("item_name" to "&6Locked-A")
            )
        )
        val second = linkedMapOf<String, Any?>(
            "__locked_display_fields__" to listOf("name"),
            "name" to mapOf("item_name" to "&bComponent Name"),
            "__locked_display_values__" to mapOf(
                "name" to mapOf("item_name" to "&6Locked-B")
            )
        )
        assertNotEquals(
            LockedDisplaySignature.read(first),
            LockedDisplaySignature.read(second)
        )
    }

    @Test
    fun `should write signature marker into runtime data`() {
        val runtime = linkedMapOf<String, Any?>(
            "__locked_display_fields__" to listOf("name"),
            "name" to mapOf("item_name" to "&6Name")
        )
        val signed = LockedDisplaySignature.withSignature(runtime)
        val signature = LockedDisplaySignature.read(runtime)
        assertEquals(signature, signed["__locked_display_signature__"])
    }

    @Test
    fun `should remove stale signature when no locked display fields`() {
        val runtime = linkedMapOf<String, Any?>(
            "__locked_display_signature__" to "stale",
            "__locked_display_values__" to mapOf(
                "name" to mapOf("item_name" to "&6Name")
            )
        )
        val signed = LockedDisplaySignature.withSignature(runtime)
        assertNull(signed["__locked_display_signature__"])
        assertNull(signed["__locked_display_values__"])
    }
}
