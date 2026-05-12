package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.event.entity.EntityDamageEvent
import org.tabooproject.baikiruto.core.item.ItemStream
import java.util.Locale

object ItemProtectionFeature {

    const val KEY_CRAFTING_VANILLA = "protection-crafting-vanilla"
    const val KEY_CRAFTING_ANY = "protection-crafting-any"
    const val KEY_CRAFTING_STATIONS = "protection-crafting-stations"
    const val KEY_CONTAINERS_DENY = "protection-containers-deny"
    const val KEY_DESTROY_ENABLED = "protection-destroy-enabled"
    const val KEY_DESTROY_CAUSES = "protection-destroy-causes"

    private val stationAliases = mapOf(
        "CRAFTING" to "CRAFTING",
        "WORKBENCH" to "CRAFTING",
        "CRAFTING_TABLE" to "CRAFTING",
        "STONECUTTER" to "STONECUTTER",
        "STONE_CUTTER" to "STONECUTTER",
        "切石机" to "STONECUTTER",
        "ENCHANTING" to "ENCHANTING",
        "ENCHANT_TABLE" to "ENCHANTING",
        "ENCHANTMENT_TABLE" to "ENCHANTING",
        "附魔台" to "ENCHANTING",
        "ANVIL" to "ANVIL",
        "铁砧" to "ANVIL",
        "GRINDSTONE" to "GRINDSTONE",
        "砂轮" to "GRINDSTONE",
        "SMITHING" to "SMITHING",
        "SMITHING_TABLE" to "SMITHING",
        "锻造台" to "SMITHING",
        "CRAFTER" to "CRAFTER",
        "合成器" to "CRAFTER"
    )

    private val containerAliases = mapOf(
        "DECORATED_POT" to "DECORATED_POT",
        "POT" to "DECORATED_POT",
        "陶罐" to "DECORATED_POT",
        "FURNACE" to "FURNACE",
        "熔炉" to "FURNACE",
        "BLAST_FURNACE" to "BLAST_FURNACE",
        "高炉" to "BLAST_FURNACE",
        "SMOKER" to "SMOKER",
        "烟熏炉" to "SMOKER",
        "ARMOR_STAND" to "ARMOR_STAND",
        "盔甲架" to "ARMOR_STAND",
        "HOPPER" to "HOPPER",
        "漏斗" to "HOPPER",
        "CRAFTER" to "CRAFTER",
        "合成器" to "CRAFTER"
    )

    private val destroyCauseAliases = mapOf(
        "ALL" to "all",
        "FIRE" to "fire",
        "FIRE_TICK" to "fire",
        "HOT_FLOOR" to "fire",
        "LAVA" to "lava",
        "CACTUS" to "cactus",
        "CONTACT" to "cactus",
        "LIGHTNING" to "lightning",
        "EXPLOSION" to "explosion",
        "BLOCK_EXPLOSION" to "explosion",
        "ENTITY_EXPLOSION" to "explosion",
        "VOID" to "void",
        "OUT_OF_WORLD" to "void"
    )

    fun blocksVanillaCrafting(stream: ItemStream): Boolean {
        return asBoolean(stream.getRuntimeData(KEY_CRAFTING_VANILLA)) == true
    }

    fun blocksAnyCrafting(stream: ItemStream): Boolean {
        return asBoolean(stream.getRuntimeData(KEY_CRAFTING_ANY)) == true
    }

    fun blocksStation(stream: ItemStream, station: String): Boolean {
        val normalizedStation = normalizeStation(station) ?: return false
        if (blocksAnyCrafting(stream)) {
            return true
        }
        return normalizedStation in parseStringList(stream.getRuntimeData(KEY_CRAFTING_STATIONS))
            .mapNotNull(::normalizeStation)
    }

    fun blocksContainer(stream: ItemStream, container: String): Boolean {
        val normalizedContainer = normalizeContainer(container) ?: return false
        return normalizedContainer in parseStringList(stream.getRuntimeData(KEY_CONTAINERS_DENY))
            .mapNotNull(::normalizeContainer)
    }

    fun isDestroyProtected(stream: ItemStream, cause: EntityDamageEvent.DamageCause): Boolean {
        if (asBoolean(stream.getRuntimeData(KEY_DESTROY_ENABLED)) != true) {
            return false
        }
        val configured = parseStringList(stream.getRuntimeData(KEY_DESTROY_CAUSES))
            .mapNotNull(::normalizeDestroyCauseToken)
        if (configured.isEmpty() || "all" in configured) {
            return true
        }
        return causeTokens(cause).any { token -> token in configured }
    }

    fun normalizeStation(source: String): String? {
        val token = normalizeToken(source) ?: return null
        return stationAliases[token]
    }

    fun normalizeContainer(source: String): String? {
        val token = normalizeToken(source) ?: return null
        return containerAliases[token] ?: token
    }

    fun normalizeDestroyCauseToken(source: String): String? {
        val token = normalizeToken(source) ?: return null
        return destroyCauseAliases[token] ?: token.lowercase(Locale.ENGLISH)
    }

    fun isFireCause(cause: EntityDamageEvent.DamageCause): Boolean {
        return "fire" in causeTokens(cause)
    }

    private fun causeTokens(cause: EntityDamageEvent.DamageCause): Set<String> {
        val normalized = normalizeDestroyCauseToken(cause.name) ?: cause.name.lowercase(Locale.ENGLISH)
        return setOf(normalized, cause.name.lowercase(Locale.ENGLISH))
    }

    private fun normalizeToken(source: String?): String? {
        return source
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.substringAfter(':')
            ?.replace('-', '_')
            ?.replace(' ', '_')
            ?.uppercase(Locale.ENGLISH)
    }

    private fun parseStringList(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> source.split(',', '\n')
            is Iterable<*> -> source.flatMap { parseStringList(it) }
            else -> listOf(source.toString())
        }.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun asBoolean(source: Any?): Boolean? {
        return when (source) {
            null -> null
            is Boolean -> source
            is Number -> source.toInt() != 0
            is String -> when (source.trim().lowercase(Locale.ENGLISH)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }
}
