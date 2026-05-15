package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ItemCooldownFeature {

    const val KEY_APPLY_ON_CANCELLED_TRIGGERS = "cooldown-apply-on-cancelled-triggers"

    private val playerCooldown = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Long>>()
    // 硬阻断：冷却期间取消事件 + 不执行脚本
    private val blockingTriggers = setOf(
        ItemScriptTrigger.USE,
        ItemScriptTrigger.INTERACT,
        ItemScriptTrigger.RIGHT_CLICK,
        ItemScriptTrigger.RIGHT_CLICK_ENTITY,
        ItemScriptTrigger.CONSUME,
        ItemScriptTrigger.SHOOT
    )

    // 软阻断：冷却期间只跳过脚本执行，不取消底层事件（保留原版伤害等行为）
    private val throttledTriggers = setOf(
        ItemScriptTrigger.ATTACK,
        ItemScriptTrigger.LEFT_CLICK
    )

    private val allCooldownTriggers = blockingTriggers + throttledTriggers

    /**
     * 冷却期间是否应取消事件（硬阻断）。
     * 仅对 USE/RIGHT_CLICK/INTERACT 等"主动使用"类触发器生效。
     */
    fun shouldBlock(stream: ItemStream, player: Player?, triggers: Collection<ItemScriptTrigger>): Boolean {
        if (triggers.none { it in blockingTriggers }) {
            return false
        }
        return remainingTicks(stream, player) > 0
    }

    /**
     * 冷却期间是否应跳过脚本执行（软阻断）。
     * 对 ATTACK/LEFT_CLICK 等"战斗"类触发器生效：不取消事件，仅抑制脚本。
     */
    fun shouldThrottle(stream: ItemStream, player: Player?, triggers: Collection<ItemScriptTrigger>): Boolean {
        if (triggers.none { it in throttledTriggers }) {
            return false
        }
        return remainingTicks(stream, player) > 0
    }

    fun applyCooldown(stream: ItemStream, player: Player?, triggers: Collection<ItemScriptTrigger>) {
        if (triggers.none { it in allCooldownTriggers }) {
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

    fun shouldApplyOnCancelled(stream: ItemStream, triggers: Collection<ItemScriptTrigger>): Boolean {
        val configured = configuredApplyOnCancelledTriggers(stream)
        if (configured.isEmpty()) {
            return false
        }
        return triggers.any { it in configured }
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

    private fun configuredApplyOnCancelledTriggers(stream: ItemStream): Set<ItemScriptTrigger> {
        return when (val raw = stream.getRuntimeData(KEY_APPLY_ON_CANCELLED_TRIGGERS)) {
            is Iterable<*> -> raw.mapNotNull { ItemScriptTrigger.fromKey(it?.toString().orEmpty()) }.toSet()
            is Array<*> -> raw.mapNotNull { ItemScriptTrigger.fromKey(it?.toString().orEmpty()) }.toSet()
            is String -> raw.split(',', ';', '|')
                .mapNotNull { ItemScriptTrigger.fromKey(it) }
                .toSet()
            else -> emptySet()
        }
    }
}
