package org.tabooproject.baikiruto.legacyapi

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.tabooproject.baikiruto.core.item.SerializedItem

class BaikirutoLegacyAPIJsonCompatTest {

    @Test
    fun `should encode and decode legacy style json container`() {
        val source = object : SerializedItem {
            override val itemId: String = "test:item"
            override val amount: Int = 3
            override val versionHash: String = "vh-1"
            override val metaHistory: List<String> = listOf("meta-a", "meta-b")
            override val runtimeData: Map<String, Any?> = linkedMapOf(
                "foo" to "bar",
                "num" to 5,
                "unique.player" to "Alice",
                "unique.date" to 12345L,
                "unique.uuid" to "uuid-1"
            )
            override val itemStackData: String = "dummy-base64"
        }
        val json = BaikirutoLegacyAPI.serializeToJson(source)
        assertEquals("test:item", json["id"].asString)
        assertEquals("test:item", json["itemId"].asString)
        assertEquals(3, json["amount"].asInt)
        assertEquals(3, json["count"].asInt)
        assertTrue(json["data"].asJsonObject.has("foo"))
        assertTrue(json["runtimeData"].asJsonObject.has("foo"))
        assertTrue(json["unique"].asJsonObject.has("uuid"))
        assertTrue(json["uniqueData"].asJsonObject.has("uuid"))
        assertTrue(json["_baikiruto"].asJsonObject.has("version_hash"))
        assertEquals("vh-1", json["versionHash"].asString)
        assertTrue(json["metaHistory"].asJsonArray.size() == 2)

        val restored = BaikirutoLegacyAPI.deserializeToSerializedItem(json)
        assertEquals("test:item", restored.itemId)
        assertEquals(3, restored.amount)
        assertEquals("vh-1", restored.versionHash)
        assertEquals(listOf("meta-a", "meta-b"), restored.metaHistory)
        assertEquals("dummy-base64", restored.itemStackData)
        assertEquals("bar", restored.runtimeData["foo"])
        assertEquals("Alice", restored.runtimeData["unique.player"])
        assertEquals("uuid-1", restored.runtimeData["unique.uuid"])
        assertEquals(true, restored.runtimeData["unique-enabled"])
        assertEquals("5", restored.runtimeData["num"])
    }

    @Test
    fun `should support fallback legacy field names`() {
        val json = JsonParser.parseString(
            """
            {
              "itemId": "fallback:item",
              "count": 2,
              "runtimeData": {"a": 1},
              "versionHash": "vh-2",
              "metaHistory": ["m1"],
              "itemStackData": "dummy-data"
            }
            """.trimIndent()
        ).asJsonObject
        val restored = BaikirutoLegacyAPI.deserializeToSerializedItem(json)
        assertEquals("fallback:item", restored.itemId)
        assertEquals(2, restored.amount)
        assertEquals("vh-2", restored.versionHash)
        assertEquals(listOf("m1"), restored.metaHistory)
        assertEquals("dummy-data", restored.itemStackData)
        assertEquals("1", restored.runtimeData["a"])
    }

    @Test
    fun `should support uniqueData alias and legacy primitive conversion`() {
        val json = JsonParser.parseString(
            """
            {
              "id": "legacy:item",
              "amount": 0,
              "data": {
                "enabled": true,
                "level": 7,
                "tags": ["a", 1]
              },
              "uniqueData": {
                "player": "Bob",
                "date": "999",
                "uuid": "u-1"
              },
              "itemStackData": "dummy-stack"
            }
            """.trimIndent()
        ).asJsonObject
        val restored = BaikirutoLegacyAPI.deserializeToSerializedItem(json)
        assertEquals(1, restored.amount)
        assertEquals("true", restored.runtimeData["enabled"])
        assertEquals("7", restored.runtimeData["level"])
        assertEquals(listOf("a", "1"), restored.runtimeData["tags"])
        assertEquals("Bob", restored.runtimeData["unique.player"])
        assertEquals(999L, restored.runtimeData["unique.date"])
        assertEquals("u-1", restored.runtimeData["unique.uuid"])
        assertEquals(true, restored.runtimeData["unique-enabled"])
    }

    @Test
    fun `should remove unique fields from data on serialize`() {
        val source = object : SerializedItem {
            override val itemId: String = "test:item"
            override val amount: Int = 1
            override val versionHash: String = "vh"
            override val metaHistory: List<String> = emptyList()
            override val runtimeData: Map<String, Any?> = linkedMapOf(
                "unique.player" to "Alice",
                "unique.date" to 1000L,
                "unique.uuid" to "uuid-x",
                "other" to "ok"
            )
            override val itemStackData: String = "stack"
        }
        val json = BaikirutoLegacyAPI.serializeToJson(source)
        val data = json["data"].asJsonObject
        assertFalse(data.has("unique.player"))
        assertFalse(data.has("unique.date"))
        assertFalse(data.has("unique.uuid"))
        assertEquals("ok", data["other"].asString)
        assertTrue(json["unique"].asJsonObject.has("uuid"))
    }

    @Test
    fun `should generate fallback itemStackData for minecraft id`() {
        val json = JsonParser.parseString(
            """
            {
              "id": "minecraft:stone",
              "amount": 2
            }
            """.trimIndent()
        ).asJsonObject
        val restored = BaikirutoLegacyAPI.deserializeToSerializedItem(json)
        assertEquals("minecraft:stone", restored.itemId)
        assertEquals(2, restored.amount)
        assertNotNull(restored.itemStackData)
        assertTrue(restored.itemStackData.isNotBlank())
    }

    @Test
    fun `should support internal alias keys and string meta history`() {
        val json = JsonParser.parseString(
            """
            {
              "id": "alias:item",
              "_baikiruto": {
                "versionHash": "vh-alias",
                "meta-history": "m1,m2",
                "item-stack-data": "stack-alias"
              },
              "unique": {
                "owner": "Eve",
                "time": "100",
                "id": "u-2",
                "enabled": "true"
              }
            }
            """.trimIndent()
        ).asJsonObject
        val restored = BaikirutoLegacyAPI.deserializeToSerializedItem(json)
        assertEquals("vh-alias", restored.versionHash)
        assertEquals(listOf("m1", "m2"), restored.metaHistory)
        assertEquals("stack-alias", restored.itemStackData)
        assertEquals("Eve", restored.runtimeData["unique.player"])
        assertEquals(100L, restored.runtimeData["unique.date"])
        assertEquals("u-2", restored.runtimeData["unique.uuid"])
        assertEquals(true, restored.runtimeData["unique-enabled"])
    }
}
