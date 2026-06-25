package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import taboolib.common.platform.event.EventPriority

/**
 * 定义每个触发器的默认事件优先级，对应现有 ItemActionListener 中的 @SubscribeEvent(priority = ...) 注解。
 */
object ItemScriptPriorityDefaults {

    private val defaults: Map<ItemScriptTrigger, EventPriority> = mapOf(
        // 高优先级事件（需要在其他插件前拦截）
        ItemScriptTrigger.INTERACT to EventPriority.HIGH,
        ItemScriptTrigger.LEFT_CLICK to EventPriority.HIGH,
        ItemScriptTrigger.RIGHT_CLICK to EventPriority.HIGH,
        ItemScriptTrigger.USE to EventPriority.HIGH,
        ItemScriptTrigger.EQUIP to EventPriority.HIGHEST,
        ItemScriptTrigger.UNEQUIP to EventPriority.HIGHEST,

        // MONITOR 优先级事件（观察模式，不取消事件）
        ItemScriptTrigger.RIGHT_CLICK_ENTITY to EventPriority.MONITOR,
        ItemScriptTrigger.ATTACK to EventPriority.MONITOR,
        ItemScriptTrigger.DAMAGE to EventPriority.MONITOR,
        ItemScriptTrigger.BLOCK_BREAK to EventPriority.MONITOR,
        ItemScriptTrigger.ITEM_BREAK to EventPriority.MONITOR,
        ItemScriptTrigger.CONSUME to EventPriority.MONITOR,
        ItemScriptTrigger.SWAP_TO_MAINHAND to EventPriority.MONITOR,
        ItemScriptTrigger.SWAP_TO_OFFHAND to EventPriority.MONITOR,
        ItemScriptTrigger.DROP to EventPriority.MONITOR,
        ItemScriptTrigger.PICKUP to EventPriority.MONITOR,
        ItemScriptTrigger.INVENTORY_CLICK to EventPriority.MONITOR,
        ItemScriptTrigger.DEATH to EventPriority.MONITOR,
        ItemScriptTrigger.KILL to EventPriority.MONITOR,
        ItemScriptTrigger.HURT to EventPriority.MONITOR,
        ItemScriptTrigger.SHOOT to EventPriority.MONITOR,
        ItemScriptTrigger.PROJECTILE_HIT to EventPriority.MONITOR,
        ItemScriptTrigger.SNEAK to EventPriority.MONITOR,
        ItemScriptTrigger.SPRINT to EventPriority.MONITOR,
        ItemScriptTrigger.JUMP to EventPriority.MONITOR,
        ItemScriptTrigger.SELECT to EventPriority.MONITOR,
        
        // NORMAL 优先级事件
        ItemScriptTrigger.RESPAWN to EventPriority.NORMAL,
        ItemScriptTrigger.ASYNC_TICK to EventPriority.NORMAL,
        
        // 不对应 Bukkit 事件的触发器（内部调用）
        ItemScriptTrigger.BUILD to EventPriority.NORMAL,
        ItemScriptTrigger.RELEASE to EventPriority.NORMAL,
        ItemScriptTrigger.RELEASE_DISPLAY to EventPriority.NORMAL
    )

    /**
     * 获取触发器的默认事件优先级。
     */
    fun getDefault(trigger: ItemScriptTrigger): EventPriority {
        return defaults[trigger] ?: EventPriority.NORMAL
    }

    /**
     * 解析配置的优先级字符串为 EventPriority 枚举。
     * 输入已经归一化为小写（如 "lowest", "highest"）。
     */
    fun parse(normalized: String?): EventPriority? {
        return when (normalized?.trim()?.lowercase()) {
            "lowest" -> EventPriority.LOWEST
            "low" -> EventPriority.LOW
            "normal" -> EventPriority.NORMAL
            "high" -> EventPriority.HIGH
            "highest" -> EventPriority.HIGHEST
            "monitor" -> EventPriority.MONITOR
            else -> null
        }
    }

    /**
     * 获取有效的事件优先级：配置优先级（如果有效）或默认优先级。
     * @param trigger 触发器
     * @param configuredPriority 配置的优先级字符串（已归一化）
     * @return 有效的 EventPriority
     */
    fun effective(trigger: ItemScriptTrigger, configuredPriority: String?): EventPriority {
        return parse(configuredPriority) ?: getDefault(trigger)
    }
}
