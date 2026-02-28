package org.tabooproject.baikiruto.core.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ItemDisplayStructureTest {

    @Test
    fun `item display should build structured product`() {
        val display = ItemDisplay(
            id = "test",
            name = mapOf("item_name" to "<quality><name>"),
            lore = mapOf("item_description" to listOf("- <lines...>", "tail: <tail>"))
        )
        val product = display.build(
            name = mapOf("quality" to "&6", "name" to "Sword"),
            lore = mapOf(
                "lines" to listOf("A", "B"),
                "tail" to listOf("END")
            )
        )
        assertEquals("&6Sword", product.name)
        assertEquals(listOf("- A", "- B", "tail: END"), product.lore)
    }

    @Test
    fun `structure single should support trim options`() {
        val structure = DefaultStructureSingle("  <value>  ")
        assertEquals("123", structure.build(mapOf("value" to "123"), trim = true))
        assertEquals("  123  ", structure.build(mapOf("value" to "123"), trim = false))
    }

    @Test
    fun `structure list should skip empty ellipsis variables`() {
        val structure = DefaultStructureList(listOf("head", "- <values...>", "tail"))
        val product = structure.build(
            vars = mapOf("values" to emptyList())
        )
        assertEquals(listOf("head", "tail"), product)
    }
}
