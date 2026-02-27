package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ItemCooldownFeature {

    private val playerCooldown = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    private val interactiveTriggers = setOf(
        ItemScriptTrigger.USE,
        ItemScriptTrigger.INTERACT,
        ItemScriptTrigger.LEFT_CLICK,
        ItemScriptTrigger.RIGHT_CLICK,
        ItemScriptTrigger.RIGHT_CLICK_ENTITY,
        ItemScriptTrigger.ATTACK,
        ItemScriptTrigger.CONSUME
    )

    fun shouldBlock(stream: ItemStream, player: Player?, triggers: Collection<ItemScriptTrigger>): Boolean {
        if (triggers.none { it in interactiveTriggers }) {
            return false
        }
        return remainingTicks(stream, player) > 0
    }

    fun applyCooldown(stream: ItemStream, player: Player?, triggers: Collection<ItemScriptTrigger>) {
        if (triggers.none { it in interactiveTriggers }) {
            return
        }
        val configured = configuredTicks(stream)
        if (configured <= 0L) {
            return
        }
        val expireAt = System.currentTimeMillis() + configured * 50L
        val cooldownKey = resolveCooldownKey(stream)
        if (isByPlayer(stream) && player != null) {
            val map = playerCooldown.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
            map[cooldownKey] = expireAt
        } else {
            stream.setRuntimeData("cooldown-expire-at", expireAt)
        }
        stream.markSignal(ItemSignal.COOLDOWN_APPLIED)
    }

    fun injectDisplayData(stream: ItemStream, player: Player?) {
        val remain = remainingTicks(stream, player).coerceAtLeast(0L)
        stream.setRuntimeData("cooldown_remaining", remain)
        stream.setRuntimeData("cooldown_remaining_seconds", remain.toDouble() / 20.0)
    }

    fun remainingTicks(stream: ItemStream, player: Player?): Long {
        return getRemainingTicks(stream, player)
    }

    fun setRemainingTicks(stream: ItemStream, player: Player?, ticks: Long) {
        val safe = ticks.coerceAtLeast(0L)
        val expireAt = System.currentTimeMillis() + safe * 50L
        val cooldownKey = resolveCooldownKey(stream)
        if (isByPlayer(stream) && player != null) {
            val map = playerCooldown.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }
            map[cooldownKey] = expireAt
        } else {
            stream.setRuntimeData("cooldown-expire-at", expireAt)
        }
        stream.markSignal(ItemSignal.COOLDOWN_APPLIED)
    }

    private fun configuredTicks(stream: ItemStream): Long {
        return when (val raw = stream.getRuntimeData("cooldown") ?: stream.getRuntimeData("cooldown-ticks")) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: 0L
            else -> 0L
        }.coerceAtLeast(0L)
    }

    private fun getRemainingTicks(stream: ItemStream, player: Player?): Long {
        val cooldownKey = resolveCooldownKey(stream)
        val expireAt = if (isByPlayer(stream) && player != null) {
            playerCooldown[player.uniqueId]?.get(cooldownKey) ?: 0L
        } else {
            when (val raw = stream.getRuntimeData("cooldown-expire-at")) {
                is Number -> raw.toLong()
                is String -> raw.trim().toLongOrNull() ?: 0L
                else -> 0L
            }
        }
        val remainMs = expireAt - System.currentTimeMillis()
        if (remainMs <= 0L) {
            return 0L
        }
        return (remainMs + 49L) / 50L
    }

    private fun isByPlayer(stream: ItemStream): Boolean {
        return when (val raw = stream.getRuntimeData("cooldown-by-player")) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.trim().equals("true", true) || raw.trim() == "1"
            else -> true
        }
    }

    private fun resolveCooldownKey(stream: ItemStream): String {
        val group = stream.getRuntimeData("use-cooldown-group")
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return group ?: stream.itemId
    }
}
