package org.tabooproject.baikiruto.impl.hook

import ink.ptms.um.event.MobDeathEvent
import ink.ptms.um.event.MobSpawnEvent
import org.bukkit.Material
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.library.configuration.ConfigurationSection
import java.util.Locale
import kotlin.random.Random

object MythicHook {

    @SubscribeEvent(bind = "ink.ptms.um.event.MobSpawnEvent")
    fun onMobSpawn(event: OptionalEvent) {
        if (!BaikirutoSettings.mythicHookEnabled) {
            return
        }
        val source = event.get<MobSpawnEvent>()
        val mob = source.mob ?: return
        val config = mob.config
        val equipmentSection = config.getConfigurationSection("Baikiruto.equipments")
            ?: config.getConfigurationSection("Zaphkiel.equipments")
            ?: return
        val entity = mob.entity as? LivingEntity ?: return
        submit(delay = 5L) {
            applyEquipments(equipmentSection, entity)
        }
    }

    @SubscribeEvent(bind = "ink.ptms.um.event.MobDeathEvent")
    fun onMobDeath(event: OptionalEvent) {
        if (!BaikirutoSettings.mythicHookEnabled) {
            return
        }
        val source = event.get<MobDeathEvent>()
        val mob = source.mob
        val config = mob.config
        val dropLines = config.getStringList("Baikiruto.drops").ifEmpty {
            config.getStringList("Zaphkiel.drops")
        }
        if (dropLines.isEmpty()) {
            return
        }

        val killer = source.killer as? Player
        val context = linkedMapOf<String, Any?>()
        if (killer != null) {
            context["player"] = killer
            context["sender"] = killer
            context["killer"] = killer
        }

        val drops = source.drop
        dropLines.forEach { line ->
            val parsed = parseDrop(line) ?: return@forEach
            if (!parsed.roll()) {
                return@forEach
            }
            val item = Baikiruto.api().getItemManager().generateItemStack(parsed.itemId, context) ?: return@forEach
            item.amount = parsed.nextAmount()
            drops += item
        }
    }

    private fun applyEquipments(section: ConfigurationSection, entity: LivingEntity) {
        section.getValues(false).forEach { (slot, raw) ->
            val value = raw?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            val item = if (value.equals("air", true)) {
                ItemStack(Material.AIR)
            } else {
                Baikiruto.api().getItemManager().generateItemStack(value) ?: ItemStack(Material.AIR)
            }
            setEquipment(entity, slot, item)
        }
    }

    private fun setEquipment(entity: LivingEntity, rawSlot: String, item: ItemStack) {
        val equipment = entity.equipment ?: return
        when (rawSlot.trim().lowercase(Locale.ENGLISH)) {
            "mainhand", "main_hand", "hand" -> {
                equipment.setItemInMainHand(item)
                equipment.itemInMainHandDropChance = 0f
            }
            "offhand", "off_hand" -> {
                equipment.setItemInOffHand(item)
                equipment.itemInOffHandDropChance = 0f
            }
            "head", "helmet" -> {
                equipment.helmet = item
                equipment.helmetDropChance = 0f
            }
            "chest", "chestplate" -> {
                equipment.chestplate = item
                equipment.chestplateDropChance = 0f
            }
            "legs", "leggings" -> {
                equipment.leggings = item
                equipment.leggingsDropChance = 0f
            }
            "feet", "boots" -> {
                equipment.boots = item
                equipment.bootsDropChance = 0f
            }
        }
    }

    private data class ParsedDrop(
        val itemId: String,
        val minAmount: Int,
        val maxAmount: Int,
        val chance: Double
    ) {

        fun roll(): Boolean {
            return chance >= 1.0 || Random.nextDouble() <= chance
        }

        fun nextAmount(): Int {
            if (minAmount >= maxAmount) {
                return minAmount
            }
            return Random.nextInt(minAmount, maxAmount + 1)
        }
    }

    private fun parseDrop(raw: String): ParsedDrop? {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return null
        }
        val itemId = tokens[0]
        val (min, max) = parseAmount(tokens.getOrNull(1))
        val chance = tokens.getOrNull(2)?.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
        return ParsedDrop(
            itemId = itemId,
            minAmount = min,
            maxAmount = max,
            chance = chance
        )
    }

    private fun parseAmount(raw: String?): Pair<Int, Int> {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) {
            return 1 to 1
        }
        val split = source.split('-', limit = 2)
        if (split.size == 1) {
            val value = split[0].toIntOrNull()?.coerceAtLeast(1) ?: 1
            return value to value
        }
        val first = split[0].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val second = split[1].toIntOrNull()?.coerceAtLeast(1) ?: first
        return if (first <= second) {
            first to second
        } else {
            second to first
        }
    }
}
