package org.tabooproject.baikiruto.core.version

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Material
import org.bukkit.NamespacedKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    private val parsePersistentCustomDataValue = DataComponentVersionAdapter::class.java.getDeclaredMethod(
        "parsePersistentCustomDataValue",
        NamespacedKey::class.java,
        Any::class.java
    ).apply {
        isAccessible = true
    }
    private val normalizeAttributeModifiers = DataComponentVersionAdapter::class.java.getDeclaredMethod(
        "normalizeAttributeModifiers",
        Any::class.java,
        Material::class.java
    ).apply {
        isAccessible = true
    }
    private val shouldPreserveDefaultAttributeModifiers = DataComponentVersionAdapter::class.java.getDeclaredMethod(
        "shouldPreserveDefaultAttributeModifiers",
        Map::class.java
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

    @Test
    fun `should expand contact damage resistant aliases into candidates`() {
        val candidates = invokeCandidates(
            "minecraft:damage_resistant",
            mapOf("types" to listOf("contact", "cactus", "sweet_berry_bush"))
        )
        val types = candidates.map { candidate ->
            val map = candidate as Map<*, *>
            map["types"]
        }

        assertTrue("#minecraft:is_contact" in types)
        assertTrue("#minecraft:contact" in types)
        assertTrue("minecraft:cactus" in types)
        assertTrue("minecraft:sweet_berry_bush" in types)
    }

    @Test
    fun `should not create damage resistant candidates when disabled`() {
        val candidates = invokeCandidates("minecraft:damage_resistant", mapOf("enabled" to false, "types" to listOf("contact")))

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `should preserve vanilla default attributes unless replacement requested`() {
        assertEquals(true, invokeShouldPreserveDefaultAttributes(mapOf("modifiers" to emptyList<Any>())))
        assertEquals(false, invokeShouldPreserveDefaultAttributes(mapOf("replace" to true, "modifiers" to emptyList<Any>())))
        assertEquals(false, invokeShouldPreserveDefaultAttributes(mapOf("preserve-defaults" to false, "modifiers" to emptyList<Any>())))
    }

    @Test
    fun `should keep configured attribute modifiers when explicit replacement requested`() {
        val modifiers = invokeAttributeModifiers(
            mapOf(
                "replace" to true,
                "modifiers" to listOf(
                    mapOf("type" to "attack_damage", "amount" to 3.0, "operation" to "add_value", "slot" to "mainhand")
                )
            ),
            Material.NETHERITE_SWORD
        )

        assertEquals(1, modifiers.size)
        assertEquals("baikiruto:attack_damage_0", modifiers.first()["id"])
    }

    @Test
    fun `should treat cmi rainbow one flag as byte persistent data`() {
        val value = invokePersistentCustomDataValue(NamespacedKey("cmilib", "cmirainbowarmor"), 1)

        assertEquals("ByteValue", value::class.simpleName)
        assertEquals(1, value::class.java.getDeclaredMethod("getValue").invoke(value))
    }

    @Test
    fun `should keep ordinary public bukkit integer flags as integer persistent data`() {
        val value = invokePersistentCustomDataValue(NamespacedKey("example", "flag"), 1)

        assertEquals("IntValue", value::class.simpleName)
        assertEquals(1, value::class.java.getDeclaredMethod("getValue").invoke(value))
    }

    @Test
    fun `should support explicit byte suffix for public bukkit values`() {
        val value = invokePersistentCustomDataValue(NamespacedKey("example", "flag"), "1b")

        assertEquals("ByteValue", value::class.simpleName)
        assertEquals(1, value::class.java.getDeclaredMethod("getValue").invoke(value))
    }

    @Test
    fun `should support explicit typed map for public bukkit values`() {
        val value = invokePersistentCustomDataValue(NamespacedKey("example", "flag"), mapOf("type" to "byte", "value" to 1))

        assertEquals("ByteValue", value::class.simpleName)
        assertEquals(1, value::class.java.getDeclaredMethod("getValue").invoke(value))
    }

    private fun invokeCandidates(componentKey: String, value: Any): List<Any> {
        @Suppress("UNCHECKED_CAST")
        return componentValueCandidates.invoke(adapter, componentKey, value) as List<Any>
    }

    private fun invokeShouldPreserve(runtimeData: Map<String, Any?>, componentKey: String): Boolean {
        return shouldPreserveLegacyDisplay.invoke(adapter, runtimeData, componentKey) as Boolean
    }

    private fun invokePersistentCustomDataValue(key: NamespacedKey, value: Any): Any {
        return parsePersistentCustomDataValue.invoke(adapter, key, value)!!
    }

    private fun invokeAttributeModifiers(value: Any, material: Material): List<Map<String, Any>> {
        @Suppress("UNCHECKED_CAST")
        return normalizeAttributeModifiers.invoke(adapter, value, material) as List<Map<String, Any>>
    }

    private fun invokeShouldPreserveDefaultAttributes(value: Map<String, Any>): Boolean {
        return shouldPreserveDefaultAttributeModifiers.invoke(adapter, value) as Boolean
    }
}
