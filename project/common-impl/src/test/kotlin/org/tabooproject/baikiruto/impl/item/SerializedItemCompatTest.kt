package org.tabooproject.baikiruto.impl.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SerializedItemCompatTest {

    @Test
    fun `should expose legacy style fields and unique data`() {
        val serialized = DefaultSerializedItem(
            itemId = "test:item",
            amount = 2,
            versionHash = "v1",
            metaHistory = listOf("meta-a"),
            runtimeData = linkedMapOf(
                "foo" to "bar",
                "unique.player" to "mical",
                "unique.date" to 123L,
                "unique.uuid" to "u-1"
            ),
            itemStackData = "AA=="
        )

        assertEquals("test:item", serialized.id)
        assertEquals("bar", serialized.data?.get("foo"))
        val unique = serialized.uniqueData
        assertNotNull(unique)
        assertEquals("mical", unique?.player)
        assertEquals(123L, unique?.date)
        assertEquals("u-1", unique?.uuid)

        val mapped = serialized.toMap()
        assertEquals("test:item", mapped["id"])
        assertEquals(2, mapped["amount"])
        assertEquals("v1", mapped["version_hash"])
    }

    @Test
    fun `should keep unique data null when no unique runtime keys`() {
        val serialized = DefaultSerializedItem(
            itemId = "test:item",
            amount = 1,
            versionHash = "v1",
            metaHistory = emptyList(),
            runtimeData = mapOf("foo" to "bar"),
            itemStackData = "AA=="
        )

        assertNull(serialized.uniqueData)
    }
}
