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

    @Test
    fun `should keep minimessage text raw when parsing disabled`() {
        try {
            LegacyTextColorizer.setMiniMessageEnabledOverride(false)
            LegacyTextColorizer.setMiniMessageAvailabilityOverride(true)
            LegacyTextColorizer.setMiniMessageTransformerOverride { "&cExample" }
            assertEquals("<red>Example</red>", LegacyTextColorizer.colorize("<red>Example</red>"))
        } finally {
            LegacyTextColorizer.clearMiniMessageOverrides()
        }
    }

    @Test
    fun `should keep minimessage text raw when runtime is unavailable`() {
        try {
            LegacyTextColorizer.setMiniMessageEnabledOverride(true)
            LegacyTextColorizer.setMiniMessageAvailabilityOverride(false)
            LegacyTextColorizer.setMiniMessageTransformerOverride { "&cExample" }
            assertEquals("<red>Example</red>", LegacyTextColorizer.colorize("<red>Example</red>"))
        } finally {
            LegacyTextColorizer.clearMiniMessageOverrides()
        }
    }

    @Test
    fun `should convert minimessage text when enabled and available`() {
        try {
            LegacyTextColorizer.setMiniMessageEnabledOverride(true)
            LegacyTextColorizer.setMiniMessageAvailabilityOverride(true)
            LegacyTextColorizer.setMiniMessageTransformerOverride { source ->
                if (source == "<red>Example</red>") "&cExample" else source
            }
            assertEquals("§cExample", LegacyTextColorizer.colorize("<red>Example</red>"))
        } finally {
            LegacyTextColorizer.clearMiniMessageOverrides()
        }
    }
}
