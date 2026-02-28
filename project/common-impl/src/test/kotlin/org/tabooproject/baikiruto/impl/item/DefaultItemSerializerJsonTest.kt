package org.tabooproject.baikiruto.impl.item

import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultItemSerializerJsonTest {

    @Test
    fun `should deserialize stream from legacy json object`() {
        val json = JsonObject().apply {
            addProperty("id", "minecraft:stone")
            addProperty("amount", 2)
            add("data", JsonObject().apply {
                addProperty("foo", "bar")
            })
            add("_baikiruto", JsonObject().apply {
                addProperty("version_hash", "v1")
                addProperty("item_stack_data", "AA==")
            })
        }

        val stream = DefaultItemSerializer.deserialize(json)

        assertEquals("minecraft:stone", stream.itemId)
        assertEquals("v1", stream.versionHash)
        assertEquals("bar", stream.runtimeData["foo"])
        assertEquals(2, stream.itemStack().amount)
    }

    @Test
    fun `should deserialize stream from legacy json string`() {
        val json = """
            {
              "id":"minecraft:stone",
              "count":1,
              "_baikiruto":{
                "versionHash":"v2",
                "itemStackData":"AA=="
              }
            }
        """.trimIndent()

        val stream = DefaultItemSerializer.deserialize(json)

        assertEquals("minecraft:stone", stream.itemId)
        assertEquals("v2", stream.versionHash)
        assertEquals(1, stream.itemStack().amount)
    }
}
