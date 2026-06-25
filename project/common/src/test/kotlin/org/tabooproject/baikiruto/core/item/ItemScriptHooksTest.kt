package org.tabooproject.baikiruto.core.item

import org.tabooproject.baikiruto.core.BaikirutoScriptSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun `should expose typed entries with default fluxon fallback`() {
        val hooks = ItemScriptHooks.fromSources(
            raw = mapOf(
                "on_use" to BaikirutoScriptSource(type = "custom_engine", content = "use()")
            )
        )

        assertEquals("use()", hooks.source(ItemScriptTrigger.USE))
        assertEquals("custom_engine", hooks.type(ItemScriptTrigger.USE))
        assertEquals(BaikirutoScriptSource.DEFAULT_TYPE, ItemScriptHooks(build = "build()")
            .type(ItemScriptTrigger.BUILD))
        assertNotNull(hooks.entry(ItemScriptTrigger.USE))
    }

    @Test
    fun `should export typed script entries`() {
        val hooks = ItemScriptHooks.fromSources(
            raw = mapOf(
                "on_use" to BaikirutoScriptSource(type = "mock", content = "use()")
            ),
            i18nRaw = mapOf(
                "zh_cn" to mapOf(
                    "on_use" to BaikirutoScriptSource(type = "mock_i18n", content = "zhUse()")
                )
            )
        )

        val scripts = hooks.toTypedScriptMap("demo:item")
        assertEquals("mock", scripts["demo:item:use"]?.normalizedType())
        assertEquals("mock_i18n", scripts["demo:item:i18n:zh_cn:use"]?.normalizedType())
    }

    @Test
    fun `should parse legacy style trigger aliases`() {
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
        val typedScripts = item.collectScriptSources()
        assertTrue(scripts.containsKey("demo:build"))
        assertTrue(scripts.containsKey("demo:meta:trace:build"))
        assertEquals("itemBuild()", typedScripts["demo:build"]?.content)
        assertEquals("metaBuild()", typedScripts["demo:meta:trace:build"]?.content)
    }

    @Test
    fun `should resolve i18n scripts with fallback`() {
        val hooks = ItemScriptHooks.from(
            raw = mapOf("on_use" to "defaultUse()"),
            i18nRaw = mapOf(
                "zh_cn" to mapOf("on_use" to "zhUse()"),
                "en_us" to mapOf("on_build" to "enBuild()")
            )
        )
        assertEquals("zhUse()", hooks.source(ItemScriptTrigger.USE, "zh-CN"))
        assertEquals("defaultUse()", hooks.source(ItemScriptTrigger.USE, "en-US"))
        assertEquals("enBuild()", hooks.source(ItemScriptTrigger.BUILD, "en_US"))
        assertEquals(BaikirutoScriptSource.DEFAULT_TYPE, hooks.type(ItemScriptTrigger.USE))
        assertEquals("defaultUse()", hooks.source(ItemScriptTrigger.USE))
    }

    @Test
    fun `should parse priority suffix from trigger key`() {
        val hooks = ItemScriptHooks.from(
            raw = mapOf(
                "on_interact@highest" to "interact()",
                "on_attack@LOW" to "attack()",
                "on_use" to "use()"
            )
        )
        assertEquals("highest", hooks.priority(ItemScriptTrigger.INTERACT))
        assertEquals("low", hooks.priority(ItemScriptTrigger.ATTACK))
        assertEquals(null, hooks.priority(ItemScriptTrigger.USE))
    }

    @Test
    fun `should parse priority from source priority field`() {
        val hooks = ItemScriptHooks.fromSources(
            raw = mapOf(
                "on_use" to BaikirutoScriptSource(content = "use()", priority = "NORMAL"),
                "on_attack" to BaikirutoScriptSource(content = "attack()", priority = "monitor")
            )
        )
        assertEquals("normal", hooks.priority(ItemScriptTrigger.USE))
        assertEquals("monitor", hooks.priority(ItemScriptTrigger.ATTACK))
    }

    @Test
    fun `should prefer key suffix priority over source priority field`() {
        val hooks = ItemScriptHooks.fromSources(
            raw = mapOf(
                "on_interact@lowest" to BaikirutoScriptSource(content = "interact()", priority = "highest")
            )
        )
        // key suffix wins
        assertEquals("lowest", hooks.priority(ItemScriptTrigger.INTERACT))
    }

    @Test
    fun `should parse cancel marker combined with priority suffix`() {
        val hooks = ItemScriptHooks.from(
            raw = mapOf(
                "on_interact!!@high" to "cancelInteract()",
                "on_attack@normal!!" to "cancelAttack()"
            )
        )
        assertTrue(hooks.shouldCancel(ItemScriptTrigger.INTERACT))
        assertEquals("high", hooks.priority(ItemScriptTrigger.INTERACT))
        assertTrue(hooks.shouldCancel(ItemScriptTrigger.ATTACK))
        assertEquals("normal", hooks.priority(ItemScriptTrigger.ATTACK))
    }

    @Test
    fun `should collect configured priorities across triggers and locales`() {
        val hooks = ItemScriptHooks.from(
            raw = mapOf("on_use@highest" to "use()"),
            i18nRaw = mapOf(
                "zh_cn" to mapOf("on_use@low" to "zhUse()"),
                "en_us" to mapOf("on_attack@monitor" to "enAttack()")
            )
        )
        val priorities = hooks.configuredPriorities()
        assertEquals(setOf("highest", "low"), priorities[ItemScriptTrigger.USE])
        assertEquals(setOf("monitor"), priorities[ItemScriptTrigger.ATTACK])
    }

    @Test
    fun `should resolve localized priority with fallback`() {
        val hooks = ItemScriptHooks.from(
            raw = mapOf("on_use@highest" to "use()"),
            i18nRaw = mapOf("zh_cn" to mapOf("on_use@low" to "zhUse()"))
        )
        assertEquals("low", hooks.priority(ItemScriptTrigger.USE, "zh-CN"))
        assertEquals("highest", hooks.priority(ItemScriptTrigger.USE, "en-US"))
    }

    @Test
    fun `should parse cancel marker from trigger key`() {
        val hooks = ItemScriptHooks.from(
            raw = mapOf(
                "on_use!!" to "cancelUse()",
                "on_build" to "build()"
            ),
            i18nRaw = mapOf(
                "zh_cn" to mapOf("on_interact!!" to "cancelInteract()")
            )
        )
        assertTrue(hooks.shouldCancel(ItemScriptTrigger.USE))
        assertTrue(hooks.shouldCancel(ItemScriptTrigger.INTERACT, "zh-CN"))
        assertTrue(hooks.shouldCancel(ItemScriptTrigger.USE, "zh-CN"))
        assertEquals("cancelUse()", hooks.source(ItemScriptTrigger.USE))
        assertEquals("cancelInteract()", hooks.source(ItemScriptTrigger.INTERACT, "zh_cn"))
    }
}
