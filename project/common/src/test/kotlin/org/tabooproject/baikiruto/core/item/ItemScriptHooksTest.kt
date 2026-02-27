package org.tabooproject.baikiruto.core.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemScriptHooksTest {

    @Test
    fun `should export non empty script entries`() {
        val hooks = ItemScriptHooks(build = "build()", drop = "drop()")
        val scripts = hooks.toScriptMap("demo:item")
        assertEquals(2, scripts.size)
        assertTrue(scripts.containsKey("demo:item:build"))
        assertTrue(scripts.containsKey("demo:item:drop"))
    }

    @Test
    fun `should parse zaphkiel style trigger aliases`() {
        val hooks = ItemScriptHooks.from(
            mapOf(
                "on_left_click" to "left()",
                "onRightClickEntity" to "entity()",
                "on_damage" to "damage()"
            )
        )
        assertEquals("left()", hooks.source(ItemScriptTrigger.LEFT_CLICK))
        assertEquals("entity()", hooks.source(ItemScriptTrigger.RIGHT_CLICK_ENTITY))
        assertEquals("damage()", hooks.source(ItemScriptTrigger.DAMAGE))
    }

    @Test
    fun `should include meta scripts in collect scripts`() {
        val item = object : Item {
            override val id: String = "demo"
            override val metas: List<Meta> = listOf(
                object : Meta {
                    override val id: String = "trace"
                    override val scripts: ItemScriptHooks = ItemScriptHooks(build = "metaBuild()")
                }
            )
            override val scripts: ItemScriptHooks = ItemScriptHooks(build = "itemBuild()")

            override fun build(context: Map<String, Any?>): ItemStream {
                throw UnsupportedOperationException("unused in this test")
            }
        }
        val scripts = item.collectScripts()
        assertTrue(scripts.containsKey("demo:build"))
        assertTrue(scripts.containsKey("demo:meta:trace:build"))
    }
}
