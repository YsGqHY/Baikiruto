package org.tabooproject.baikiruto.impl.item

import taboolib.library.configuration.ConfigurationSection
import org.tabooproject.baikiruto.core.item.ItemModel
import java.lang.reflect.Proxy
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
    fun `should collect locked data paths metadata`() {
        val parsed = invokeMapMethod(
            "parseData",
            mapOf(
                "root!!" to mapOf(
                    "child" to "value"
                ),
                "plain" to mapOf(
                    "deep!!" to 2
                )
            )
        )

        val lockedPaths = parsed["__locked_data_paths__"] as List<*>
        assertTrue("root" in lockedPaths)
        assertTrue("plain.deep" in lockedPaths)
        val root = parsed["root"] as Map<*, *>
        assertEquals("value", root["child"])
    }

    @Test
    fun `should collect locked display fields metadata`() {
        val section = sectionOf(
            mapOf(
                "name!!" to mapOf("item_name" to "&6Locked"),
                "lore" to listOf("&7normal")
            )
        )
        val metadata = invokeSectionModelMapMethod(
            "parseDisplayLockMetadata",
            section,
            listOf(ItemModel("model-1", mapOf("icon!!" to "STONE", "lore!!" to listOf("&aL"))))
        )
        val fields = metadata["__locked_display_fields__"] as List<*>
        assertTrue("name" in fields)
        assertTrue("lore" in fields)
        assertTrue("icon" in fields)
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
        val section = sectionOf(mapOf("lore" to listOf("&aLine-1\n&bLine-2", "&eLine-3")))
        assertEquals(
            listOf("&aLine-1", "&bLine-2", "&eLine-3"),
            invokeSectionListMethod("parseLore", section)
        )
    }

    @Test
    fun `should split newline in direct lore map list`() {
        val section = sectionOf(mapOf("lore" to listOf("&aLine-1\n&bLine-2", "&eLine-3")))
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
    private fun invokeSectionModelMapMethod(
        name: String,
        section: ConfigurationSection,
        models: List<ItemModel>
    ): Map<String, Any?> {
        val method = ItemDefinitionLoader::class.java.getDeclaredMethod(
            name,
            ConfigurationSection::class.java,
            List::class.java
        )
        method.isAccessible = true
        return method.invoke(ItemDefinitionLoader, section, models) as Map<String, Any?>
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

    private fun sectionOf(
        data: Map<String, Any?>,
        name: String = "root",
        parent: ConfigurationSection? = null
    ): ConfigurationSection {
        fun resolve(path: String): Any? {
            if (path.isBlank()) {
                return data
            }
            return path.split('.')
                .filter { it.isNotBlank() }
                .fold(data as Any?) { current, key ->
                    (current as? Map<*, *>)?.get(key)
                }
        }

        return Proxy.newProxyInstance(
            ConfigurationSection::class.java.classLoader,
            arrayOf(ConfigurationSection::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getPrimitiveConfig" -> null
                "getParent" -> parent
                "getName" -> name
                "setName" -> null
                "getType" -> null
                "getKeys" -> data.keys
                "contains", "isSet" -> resolve(args?.get(0) as String) != null
                "get" -> {
                    val path = args?.get(0) as String
                    val default = args.getOrNull(1)
                    resolve(path) ?: default
                }
                "set" -> null
                "getString" -> {
                    val value = resolve(args?.get(0) as String)
                    (value as? String) ?: value?.toString() ?: args?.getOrNull(1)
                }
                "isString" -> resolve(args?.get(0) as String) is String
                "getInt" -> {
                    val value = resolve(args?.get(0) as String)
                    when (value) {
                        is Number -> value.toInt()
                        is String -> value.toIntOrNull()
                        else -> args?.getOrNull(1) ?: 0
                    }
                }
                "isInt" -> {
                    val value = resolve(args?.get(0) as String)
                    value is Number || (value is String && value.toIntOrNull() != null)
                }
                "getBoolean" -> {
                    val value = resolve(args?.get(0) as String)
                    when (value) {
                        is Boolean -> value
                        is Number -> value.toInt() != 0
                        is String -> value.equals("true", true) || value == "1"
                        else -> args?.getOrNull(1) ?: false
                    }
                }
                "isBoolean" -> resolve(args?.get(0) as String) is Boolean
                "getDouble" -> {
                    val value = resolve(args?.get(0) as String)
                    when (value) {
                        is Number -> value.toDouble()
                        is String -> value.toDoubleOrNull()
                        else -> args?.getOrNull(1) ?: 0.0
                    }
                }
                "isDouble" -> {
                    val value = resolve(args?.get(0) as String)
                    value is Number || (value is String && value.toDoubleOrNull() != null)
                }
                "getLong" -> {
                    val value = resolve(args?.get(0) as String)
                    when (value) {
                        is Number -> value.toLong()
                        is String -> value.toLongOrNull()
                        else -> args?.getOrNull(1) ?: 0L
                    }
                }
                "isLong" -> {
                    val value = resolve(args?.get(0) as String)
                    value is Number || (value is String && value.toLongOrNull() != null)
                }
                "getList" -> {
                    val value = resolve(args?.get(0) as String)
                    value as? List<*> ?: args?.getOrNull(1) as? List<*> ?: emptyList<Any?>()
                }
                "isList" -> resolve(args?.get(0) as String) is List<*>
                "getStringList" -> {
                    val value = resolve(args?.get(0) as String)
                    when (value) {
                        is List<*> -> value.mapNotNull { it?.toString() }
                        is String -> listOf(value)
                        else -> emptyList<String>()
                    }
                }
                "getIntegerList", "getBooleanList", "getDoubleList", "getFloatList",
                "getLongList", "getByteList", "getCharacterList", "getShortList",
                "getMapList", "getEnumList" -> emptyList<Any>()
                "getConfigurationSection" -> {
                    val value = resolve(args?.get(0) as String)
                    when (value) {
                        is ConfigurationSection -> value
                        is Map<*, *> -> sectionOf(value as Map<String, Any?>, args[0] as String, null)
                        else -> null
                    }
                }
                "isConfigurationSection" -> resolve(args?.get(0) as String) is Map<*, *>
                "getEnum" -> null
                "createSection" -> {
                    val key = args?.get(0) as String
                    sectionOf(emptyMap(), key, null)
                }
                "toMap", "getValues" -> data
                "getComment" -> null
                "getComments" -> emptyList<String>()
                "setComment", "setComments", "addComments", "clear" -> null
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Double.TYPE -> 0.0
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as ConfigurationSection
    }
}
