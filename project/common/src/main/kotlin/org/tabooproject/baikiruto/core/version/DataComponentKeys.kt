package org.tabooproject.baikiruto.core.version

/**
 * Data Component 键常量
 * 参考 Minecraft 1.20.5+ 的 Data Component 系统
 *
 * 完整列表参考：
 * https://minecraft.wiki/w/Data_component_format
 */
object DataComponentKeys {

    // 基础组件
    const val CUSTOM_DATA = "minecraft:custom_data"
    const val MAX_STACK_SIZE = "minecraft:max_stack_size"
    const val MAX_DAMAGE = "minecraft:max_damage"
    const val DAMAGE = "minecraft:damage"
    const val UNBREAKABLE = "minecraft:unbreakable"
    const val RARITY = "minecraft:rarity"

    // 显示组件
    const val CUSTOM_NAME = "minecraft:custom_name"
    const val ITEM_NAME = "minecraft:item_name"
    const val LORE = "minecraft:lore"
    const val CUSTOM_MODEL_DATA = "minecraft:custom_model_data"
    const val ITEM_MODEL = "minecraft:item_model"
    const val HIDE_TOOLTIP = "minecraft:hide_tooltip"
    const val TOOLTIP_STYLE = "minecraft:tooltip_style"
    const val HIDE_ADDITIONAL_TOOLTIP = "minecraft:hide_additional_tooltip"

    // 附魔和属性
    const val ENCHANTMENTS = "minecraft:enchantments"
    const val STORED_ENCHANTMENTS = "minecraft:stored_enchantments"
    const val ENCHANTMENT_GLINT_OVERRIDE = "minecraft:enchantment_glint_override"
    const val ATTRIBUTE_MODIFIERS = "minecraft:attribute_modifiers"

    // 染色和颜色
    const val DYED_COLOR = "minecraft:dyed_color"
    const val MAP_COLOR = "minecraft:map_color"

    // 方块相关
    const val CAN_BREAK = "minecraft:can_break"
    const val CAN_PLACE_ON = "minecraft:can_place_on"
    const val BLOCK_STATE = "minecraft:block_state"

    // 容器和物品
    const val CONTAINER = "minecraft:container"
    const val BUNDLE_CONTENTS = "minecraft:bundle_contents"
    const val CHARGED_PROJECTILES = "minecraft:charged_projectiles"

    // 食物和药水
    const val FOOD = "minecraft:food"
    const val POTION_CONTENTS = "minecraft:potion_contents"
    const val SUSPICIOUS_STEW_EFFECTS = "minecraft:suspicious_stew_effects"

    // 书籍和文本
    const val WRITTEN_BOOK_CONTENT = "minecraft:written_book_content"
    const val WRITABLE_BOOK_CONTENT = "minecraft:writable_book_content"

    // 装饰和特效
    const val TRIM = "minecraft:trim"
    const val FIREWORKS = "minecraft:fireworks"
    const val FIREWORK_EXPLOSION = "minecraft:firework_explosion"
    const val PROFILE = "minecraft:profile"

    // 工具和武器
    const val TOOL = "minecraft:tool"
    const val REPAIR_COST = "minecraft:repair_cost"

    // 音乐和声音
    const val INSTRUMENT = "minecraft:instrument"
    const val JUKEBOX_PLAYABLE = "minecraft:jukebox_playable"

    // 地图
    const val MAP_ID = "minecraft:map_id"
    const val MAP_DECORATIONS = "minecraft:map_decorations"
    const val MAP_POST_PROCESSING = "minecraft:map_post_processing"

    // 实体相关
    const val ENTITY_DATA = "minecraft:entity_data"
    const val BUCKET_ENTITY_DATA = "minecraft:bucket_entity_data"

    // 其他
    const val CREATIVE_SLOT_LOCK = "minecraft:creative_slot_lock"
    const val LOCK = "minecraft:lock"
    const val NOTE_BLOCK_SOUND = "minecraft:note_block_sound"
    const val RECIPES = "minecraft:recipes"
    const val LODESTONE_TRACKER = "minecraft:lodestone_tracker"
    const val OMINOUS_BOTTLE_AMPLIFIER = "minecraft:ominous_bottle_amplifier"

    // 1.21+ 新增
    const val CONSUMABLE = "minecraft:consumable"
    const val USE_REMAINDER = "minecraft:use_remainder"
    const val USE_COOLDOWN = "minecraft:use_cooldown"
    const val EQUIPPABLE = "minecraft:equippable"
    const val GLIDER = "minecraft:glider"
    const val DAMAGE_RESISTANT = "minecraft:damage_resistant"
    const val DEATH_PROTECTION = "minecraft:death_protection"
    const val ENCHANTABLE = "minecraft:enchantable"
    const val REPAIRABLE = "minecraft:repairable"
    const val TOOLTIP_DISPLAY = "minecraft:tooltip_display"
    const val INTANGIBLE_PROJECTILE = "minecraft:intangible_projectile"
    const val WEAPON = "minecraft:weapon"
    const val BLOCKS_ATTACKS = "minecraft:blocks_attacks"
    const val POTION_DURATION_SCALE = "minecraft:potion_duration_scale"
    const val DEBUG_STICK_STATE = "minecraft:debug_stick_state"
    const val BLOCK_ENTITY_DATA = "minecraft:block_entity_data"
    const val PROVIDES_TRIM_MATERIAL = "minecraft:provides_trim_material"
    const val PROVIDES_BANNER_PATTERNS = "minecraft:provides_banner_patterns"
    const val BANNER_PATTERNS = "minecraft:banner_patterns"
    const val BASE_COLOR = "minecraft:base_color"
    const val POT_DECORATIONS = "minecraft:pot_decorations"
    const val BEES = "minecraft:bees"
    const val CONTAINER_LOOT = "minecraft:container_loot"
    const val BREAK_SOUND = "minecraft:break_sound"
    const val VILLAGER_VARIANT = "minecraft:villager_variant"
    const val WOLF_VARIANT = "minecraft:wolf_variant"
    const val WOLF_SOUND_VARIANT = "minecraft:wolf_sound_variant"
    const val WOLF_COLLAR = "minecraft:wolf_collar"
    const val FOX_VARIANT = "minecraft:fox_variant"
    const val SALMON_SIZE = "minecraft:salmon_size"
    const val PARROT_VARIANT = "minecraft:parrot_variant"
    const val TROPICAL_FISH_PATTERN = "minecraft:tropical_fish_pattern"
    const val TROPICAL_FISH_BASE_COLOR = "minecraft:tropical_fish_base_color"
    const val TROPICAL_FISH_PATTERN_COLOR = "minecraft:tropical_fish_pattern_color"
    const val MOOSHROOM_VARIANT = "minecraft:mooshroom_variant"
    const val RABBIT_VARIANT = "minecraft:rabbit_variant"
    const val PIG_VARIANT = "minecraft:pig_variant"
    const val COW_VARIANT = "minecraft:cow_variant"
    const val CHICKEN_VARIANT = "minecraft:chicken_variant"
    const val FROG_VARIANT = "minecraft:frog_variant"
    const val HORSE_VARIANT = "minecraft:horse_variant"
    const val PAINTING_VARIANT = "minecraft:painting_variant"
    const val LLAMA_VARIANT = "minecraft:llama_variant"
    const val AXOLOTL_VARIANT = "minecraft:axolotl_variant"
    const val CAT_VARIANT = "minecraft:cat_variant"
    const val CAT_COLLAR = "minecraft:cat_collar"
    const val SHEEP_COLOR = "minecraft:sheep_color"
    const val SHULKER_COLOR = "minecraft:shulker_color"

    /**
     * 规范化组件键
     */
    fun normalize(key: String): String {
        val trimmed = key.trim().lowercase().replace('-', '_')
        return if (trimmed.startsWith("minecraft:")) {
            trimmed
        } else {
            "minecraft:$trimmed"
        }
    }

    /**
     * 检查是否为有效的组件键
     */
    fun isValid(key: String): Boolean {
        val normalized = normalize(key)
        return normalized.startsWith("minecraft:") && normalized.length > 10
    }
}
