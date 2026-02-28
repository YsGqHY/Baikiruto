package org.tabooproject.baikiruto.impl.item

import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyTextColorizerTest {

    @Test
    fun `should translate legacy color in single line`() {
        assertEquals("§6Example", LegacyTextColorizer.colorize("&6Example"))
        assertEquals("§aHello §fWorld", LegacyTextColorizer.colorize("&aHello &fWorld"))
    }

    @Test
    fun `should translate legacy color in lore lines`() {
        val colored = LegacyTextColorizer.colorize(
            listOf("&7Line 1", "&bLine 2")
        )
        assertEquals(listOf("§7Line 1", "§bLine 2"), colored)
    }
}
