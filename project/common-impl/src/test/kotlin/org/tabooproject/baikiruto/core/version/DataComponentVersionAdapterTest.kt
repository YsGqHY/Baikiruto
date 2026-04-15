package org.tabooproject.baikiruto.core.version

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DataComponentVersionAdapterTest {

    private val adapter = DataComponentVersionAdapter()
    private val componentValueCandidates = DataComponentVersionAdapter::class.java.getDeclaredMethod(
        "componentValueCandidates",
        String::class.java,
        Any::class.java
    ).apply {
        isAccessible = true
    }
    private val shouldPreserveLegacyDisplay = DataComponentVersionAdapter::class.java.getDeclaredMethod(
        "shouldPreserveLegacyDisplay",
        Map::class.java,
        String::class.java
    ).apply {
        isAccessible = true
    }

    @Test
    fun `should convert custom name legacy color into json text component candidate`() {
        val candidates = invokeCandidates("minecraft:custom_name", "&6Example All Features")
        val component = assertIs<JsonObject>(candidates.first())

        assertEquals("Example All Features", component["text"].asString)
        assertEquals("gold", component["color"].asString)
        assertEquals(false, component["italic"].asBoolean)
        assertEquals("&6Example All Features", candidates.last())
    }

    @Test
    fun `should convert lore legacy colors into json text component array candidate`() {
        val rawLore = listOf(
            "&71.21.11 data component showcase",
            "&8Includes script/meta/component pipeline"
        )

        val candidates = invokeCandidates("minecraft:lore", rawLore)
        val lore = assertIs<JsonArray>(candidates.first())
        val firstLine = lore[0].asJsonObject
        val secondLine = lore[1].asJsonObject

        assertEquals("1.21.11 data component showcase", firstLine["text"].asString)
        assertEquals("gray", firstLine["color"].asString)
        assertEquals(false, firstLine["italic"].asBoolean)
        assertEquals("Includes script/meta/component pipeline", secondLine["text"].asString)
        assertEquals("dark_gray", secondLine["color"].asString)
        assertEquals(false, secondLine["italic"].asBoolean)
        assertEquals(rawLore, candidates.last())
    }

    @Test
    fun `should allow high version display components when legacy meta display exists`() {
        val runtimeData = mapOf(
            "name" to mapOf("item_name" to "&6Legacy Name"),
            "lore" to mapOf("item_description" to listOf("&7Legacy Lore"))
        )

        assertEquals(false, invokeShouldPreserve(runtimeData, "minecraft:custom_name"))
        assertEquals(false, invokeShouldPreserve(runtimeData, "minecraft:item_name"))
        assertEquals(false, invokeShouldPreserve(runtimeData, "minecraft:lore"))
        assertEquals(false, invokeShouldPreserve(runtimeData, "minecraft:custom_data"))
    }

    private fun invokeCandidates(componentKey: String, value: Any): List<Any> {
        @Suppress("UNCHECKED_CAST")
        return componentValueCandidates.invoke(adapter, componentKey, value) as List<Any>
    }

    private fun invokeShouldPreserve(runtimeData: Map<String, Any?>, componentKey: String): Boolean {
        return shouldPreserveLegacyDisplay.invoke(adapter, runtimeData, componentKey) as Boolean
    }
}
