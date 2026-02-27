package org.tabooproject.baikiruto.core.item

enum class ItemScriptTrigger {
    BUILD,
    RELEASE,
    RELEASE_DISPLAY,
    DROP,
    USE,
    INTERACT,
    LEFT_CLICK,
    RIGHT_CLICK,
    RIGHT_CLICK_ENTITY,
    ATTACK,
    DAMAGE,
    BLOCK_BREAK,
    ITEM_BREAK,
    CONSUME,
    PICKUP,
    SWAP_TO_MAINHAND,
    SWAP_TO_OFFHAND,
    INVENTORY_CLICK,
    SELECT,
    ASYNC_TICK;

    val key: String
        get() = name.lowercase()

    companion object {

        private val aliases: Map<String, ItemScriptTrigger> = buildMap {
            values().forEach { trigger ->
                put(normalize(trigger.name), trigger)
                put(normalize(trigger.key), trigger)
            }

            alias(BUILD, "on_build", "onbuild")
            alias(RELEASE, "on_release", "onrelease")
            alias(RELEASE_DISPLAY, "on_release_display", "onreleasedisplay")
            alias(DROP, "on_drop", "ondrop")
            alias(USE, "on_use", "onuse")
            alias(INTERACT, "on_interact", "oninteract")
            alias(LEFT_CLICK, "on_left_click", "onleftclick")
            alias(RIGHT_CLICK, "on_right_click", "onrightclick")
            alias(RIGHT_CLICK_ENTITY, "on_right_click_entity", "onrightclickentity")
            alias(ATTACK, "on_attack", "onattack", "on_sword", "onsword")
            alias(DAMAGE, "on_damage", "ondamage")
            alias(BLOCK_BREAK, "on_block_break", "onblockbreak")
            alias(ITEM_BREAK, "on_item_break", "onitembreak")
            alias(CONSUME, "on_consume", "onconsume")
            alias(PICKUP, "on_pick", "on_pickup", "onpick", "onpickup")
            alias(SWAP_TO_MAINHAND, "on_swap_to_mainhand", "onswaptomainhand")
            alias(SWAP_TO_OFFHAND, "on_swap_to_offhand", "onswaptooffhand")
            alias(INVENTORY_CLICK, "on_inventory_click", "oninventoryclick", "on_click", "onclick")
            alias(SELECT, "on_select", "onselect")
            alias(ASYNC_TICK, "on_async_tick", "onasynctick", "on_tick", "ontick")
        }

        fun fromKey(rawKey: String): ItemScriptTrigger? {
            return aliases[normalize(rawKey)]
        }

        private fun MutableMap<String, ItemScriptTrigger>.alias(trigger: ItemScriptTrigger, vararg keys: String) {
            keys.forEach { key -> put(normalize(key), trigger) }
        }

        private fun normalize(value: String): String {
            return value.trim()
                .replace('-', '_')
                .lowercase()
        }
    }
}
