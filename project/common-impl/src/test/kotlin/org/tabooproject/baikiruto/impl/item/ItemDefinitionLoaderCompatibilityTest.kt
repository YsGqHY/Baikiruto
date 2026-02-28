package org.tabooproject.baikiruto.impl.item

import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemDefinitionLoaderCompatibilityTest {

    @Test
    fun `should parse simplified custom name component`() {
        val effects = invokeMapMethod(
            "parseComponents",
            mapOf(
                "minecraft:custom_name" to """{"text":"Example All Features","color":"gold","italic":false}""",
                "custom_name" to mapOf(
                    "text" to "Example Tree",
                    "color" to "gold",
                    "italic" to false
                )
            )
        )
        val name = effects["name"] as Map<*, *>
        assertEquals("&6Example Tree", name["item_name"])
    }

    @Test
    fun `should parse component lore and strip minecraft prefix`() {
        val effects = invokeMapMethod(
            "parseComponents",
            mapOf(
                "minecraft:lore" to listOf(
                    """{"text":"Line 1","color":"yellow","italic":false}""",
                    "&aLine 2"
                ),
                "minecraft:custom-model-data" to 1001
            )
        )

        val lore = (effects["lore"] as Map<*, *>)["item_description"] as List<*>
        assertEquals(listOf("&eLine 1", "&aLine 2"), lore)
        assertEquals(1001, effects["custom-model-data"])
    }

    @Test
    fun `should normalize locked data key suffix recursively`() {
        val normalized = invokeMapMethod(
            "normalizeLockedMap",
            mapOf(
                "root!!" to mapOf(
                    "child!!" to "value",
                    "plain" to listOf(
                        mapOf("node!!" to 1)
                    )
                ),
                "amount!!" to 3
            )
        )

        assertTrue("root" in normalized.keys)
        assertTrue("amount" in normalized.keys)
        val root = normalized["root"] as Map<*, *>
        assertEquals("value", root["child"])
        val plain = root["plain"] as List<*>
        val first = plain.first() as Map<*, *>
        assertEquals(1, first["node"])
    }

    @Test
    fun `should parse script value from string list and map source`() {
        val fromList = invokeStringMethod("parseScriptValue", listOf("line-1", "line-2"))
        val fromMap = invokeStringMethod(
            "parseScriptValue",
            mapOf("source" to listOf("a()", "b()"))
        )
        assertEquals("line-1\nline-2", fromList)
        assertEquals("a()\nb()", fromMap)
    }

    @Test
    fun `should keep legacy color code when wrapping lore`() {
        val ampersand = invokeListMethod("wrapLine", "&6abcdef", 4)
        val section = invokeListMethod("wrapLine", "§6abcdef", 4)
        assertEquals(listOf("&6ab", "&6cd", "&6ef"), ampersand)
        assertEquals(listOf("§6ab", "§6cd", "§6ef"), section)
    }

    @Test
    fun `should split newline in direct lore list`() {
        val section = yamlSection {
            set("lore", listOf("&aLine-1\n&bLine-2", "&eLine-3"))
        }
        assertEquals(
            listOf("&aLine-1", "&bLine-2", "&eLine-3"),
            invokeSectionListMethod("parseLore", section)
        )
    }

    @Test
    fun `should split newline in direct lore map list`() {
        val section = yamlSection {
            set("lore", listOf("&aLine-1\n&bLine-2", "&eLine-3"))
        }
        val loreMap = invokeSectionMapMethod("parseLoreMap", section)
        assertEquals(
            listOf("&aLine-1", "&bLine-2", "&eLine-3"),
            loreMap["item_description"]
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeMapMethod(name: String, source: Map<String, Any?>): Map<String, Any?> {
        val method = ItemDefinitionLoader::class.java.getDeclaredMethod(name, Map::class.java)
        method.isAccessible = true
        return method.invoke(ItemDefinitionLoader, source) as Map<String, Any?>
    }

    private fun invokeStringMethod(name: String, source: Any?): String? {
        val method = ItemDefinitionLoader::class.java.getDeclaredMethod(name, Any::class.java)
        method.isAccessible = true
        return method.invoke(ItemDefinitionLoader, source) as String?
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeSectionMapMethod(name: String, section: ConfigurationSection): Map<String, Any?> {
        val method = ItemDefinitionLoader::class.java.getDeclaredMethod(name, ConfigurationSection::class.java)
        method.isAccessible = true
        return method.invoke(ItemDefinitionLoader, section) as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeSectionListMethod(name: String, section: ConfigurationSection): List<String> {
        val method = ItemDefinitionLoader::class.java.getDeclaredMethod(name, ConfigurationSection::class.java)
        method.isAccessible = true
        return method.invoke(ItemDefinitionLoader, section) as List<String>
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeListMethod(name: String, line: String, size: Int): List<String> {
        val method = ItemDefinitionLoader::class.java.getDeclaredMethod(
            name,
            String::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(ItemDefinitionLoader, line, size) as List<String>
    }

    private fun yamlSection(builder: Configuration.() -> Unit): ConfigurationSection {
        return Configuration.empty(Type.YAML).apply(builder)
    }
}
