package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import taboolib.common.platform.event.EventPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemScriptPriorityDefaultsTest {

    @Test
    fun `should parse all valid priority strings`() {
        assertEquals(EventPriority.LOWEST, ItemScriptPriorityDefaults.parse("lowest"))
        assertEquals(EventPriority.LOW, ItemScriptPriorityDefaults.parse("low"))
        assertEquals(EventPriority.NORMAL, ItemScriptPriorityDefaults.parse("normal"))
        assertEquals(EventPriority.HIGH, ItemScriptPriorityDefaults.parse("high"))
        assertEquals(EventPriority.HIGHEST, ItemScriptPriorityDefaults.parse("highest"))
        assertEquals(EventPriority.MONITOR, ItemScriptPriorityDefaults.parse("monitor"))
    }

    @Test
    fun `should tolerate case and whitespace`() {
        assertEquals(EventPriority.HIGHEST, ItemScriptPriorityDefaults.parse("  HIGHEST "))
        assertEquals(EventPriority.LOW, ItemScriptPriorityDefaults.parse("Low"))
    }

    @Test
    fun `should return null for invalid or empty priority`() {
        assertNull(ItemScriptPriorityDefaults.parse(null))
        assertNull(ItemScriptPriorityDefaults.parse(""))
        assertNull(ItemScriptPriorityDefaults.parse("urgent"))
    }

    @Test
    fun `should expose version default priorities per trigger`() {
        // 高优先级拦截类事件
        assertEquals(EventPriority.HIGH, ItemScriptPriorityDefaults.getDefault(ItemScriptTrigger.INTERACT))
        assertEquals(EventPriority.HIGH, ItemScriptPriorityDefaults.getDefault(ItemScriptTrigger.USE))
        assertEquals(EventPriority.HIGHEST, ItemScriptPriorityDefaults.getDefault(ItemScriptTrigger.EQUIP))
        // 观察类事件
        assertEquals(EventPriority.MONITOR, ItemScriptPriorityDefaults.getDefault(ItemScriptTrigger.ATTACK))
        assertEquals(EventPriority.MONITOR, ItemScriptPriorityDefaults.getDefault(ItemScriptTrigger.DROP))
        // 普通事件
        assertEquals(EventPriority.NORMAL, ItemScriptPriorityDefaults.getDefault(ItemScriptTrigger.RESPAWN))
    }

    @Test
    fun `effective should fall back to default when unconfigured or invalid`() {
        // 未配置：回退默认
        assertEquals(EventPriority.HIGH, ItemScriptPriorityDefaults.effective(ItemScriptTrigger.INTERACT, null))
        assertEquals(EventPriority.MONITOR, ItemScriptPriorityDefaults.effective(ItemScriptTrigger.ATTACK, ""))
        // 非法值：回退默认
        assertEquals(EventPriority.MONITOR, ItemScriptPriorityDefaults.effective(ItemScriptTrigger.ATTACK, "bogus"))
        // 有效配置：覆盖默认
        assertEquals(EventPriority.LOWEST, ItemScriptPriorityDefaults.effective(ItemScriptTrigger.INTERACT, "lowest"))
    }

    @Test
    fun `every trigger should resolve a non null default`() {
        ItemScriptTrigger.values().forEach { trigger ->
            // getDefault 永不返回 null（缺失时回退 NORMAL）
            assertEquals(
                ItemScriptPriorityDefaults.getDefault(trigger),
                ItemScriptPriorityDefaults.effective(trigger, null)
            )
        }
    }
}
