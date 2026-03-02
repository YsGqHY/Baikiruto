package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tabooproject.baikiruto.impl.item.feature.ItemDurabilityFeature

class DefaultItemStreamDisplayLockTest {

    @Test
    fun `should render runtime placeholders for locked lore`() {
        val stream = DefaultItemStream(
            backingItem = ItemStack(Material.STONE),
            itemId = "test:item",
            versionHash = "v1",
            initialRuntimeData = linkedMapOf(
                "__locked_display_fields__" to listOf("lore"),
                "__locked_display_values__" to mapOf(
                    "lore" to mapOf(
                        "item_description" to listOf(
                            "&7Durability: {durability_current}/{durability_max} {durability_bar}"
                        )
                    )
                ),
                "durability" to 240,
                "durability_current" to 120
            )
        )

        ItemDurabilityFeature.prepare(stream)
        val templates = invokeNoArg(stream, "lockedLoreTemplates") as List<*>
        val context = invokeNoArg(stream, "runtimeTemplateContext")
        val rendered = invokeWithContext(stream, "renderLoreTemplates", templates.filterIsInstance<String>(), context)

        assertTrue(rendered.isNotEmpty())
        assertTrue(rendered[0].contains("120/240"))
        assertFalse(rendered[0].contains("{durability_current}"))
        assertFalse(rendered[0].contains("{durability_max}"))
        assertFalse(rendered[0].contains("{durability_bar}"))
    }

    @Test
    fun `should fallback unique player and replace last trigger placeholder`() {
        val stream = DefaultItemStream(
            backingItem = ItemStack(Material.STONE),
            itemId = "test:item",
            versionHash = "v1",
            initialRuntimeData = linkedMapOf(
                "__locked_display_fields__" to listOf("lore"),
                "__locked_display_values__" to mapOf(
                    "lore" to mapOf(
                        "item_description" to listOf(
                            "&7Owner: {unique.player}",
                            "&7Last trigger: {last_trigger}"
                        )
                    )
                )
            )
        )
        stream.rememberInvocationContext(
            mapOf(
                "player" to "Tester"
            )
        )

        val templates = invokeNoArg(stream, "lockedLoreTemplates") as List<*>
        val context = invokeNoArg(stream, "runtimeTemplateContext")
        val rendered = invokeWithContext(stream, "renderLoreTemplates", templates.filterIsInstance<String>(), context)

        assertTrue(rendered.isNotEmpty())
        assertTrue(rendered[0].contains("Tester"))
        assertFalse(rendered[0].contains("{unique.player}"))
        assertFalse(rendered[1].contains("{last_trigger}"))
    }

    private fun invokeNoArg(target: Any, methodName: String): Any {
        val method = target::class.java.getDeclaredMethod(methodName)
        method.isAccessible = true
        return method.invoke(target)
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeWithContext(
        target: Any,
        methodName: String,
        templates: List<String>,
        context: Any
    ): List<String> {
        val method = target::class.java.getDeclaredMethod(
            methodName,
            List::class.java,
            context::class.java
        )
        method.isAccessible = true
        return method.invoke(target, templates, context) as List<String>
    }
}
