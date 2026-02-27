package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.item.DefaultItemStream
import java.util.UUID

object ItemUniqueFeature {

    private const val KEY_ENABLED = "unique-enabled"
    private const val KEY_BIND_PLAYER = "unique-bind-player"
    private const val KEY_UUID = "unique.uuid"
    private const val KEY_DATE = "unique.date"
    private const val KEY_PLAYER = "unique.player"
    private const val KEY_DENY = "unique-deny-message"

    data class OwnershipResult(
        val allowed: Boolean,
        val changed: Boolean,
        val owner: String?
    )

    fun prepare(stream: DefaultItemStream, player: Player?) {
        if (!isEnabled(stream)) {
            return
        }
        if (stream.getRuntimeData(KEY_UUID) == null) {
            stream.setRuntimeData(KEY_UUID, UUID.randomUUID().toString())
        }
        if (stream.getRuntimeData(KEY_DATE) == null) {
            stream.setRuntimeData(KEY_DATE, System.currentTimeMillis())
        }
        if (isBindPlayer(stream) && player != null) {
            stream.setRuntimeData(KEY_PLAYER, player.name)
        }
    }

    fun checkOwnership(stream: ItemStream, player: Player?): OwnershipResult {
        if (!isEnabled(stream)) {
            return OwnershipResult(allowed = true, changed = false, owner = null)
        }
        if (!isBindPlayer(stream)) {
            return OwnershipResult(allowed = true, changed = false, owner = owner(stream))
        }
        val owner = owner(stream)
        if (owner.isNullOrBlank()) {
            if (player == null) {
                return OwnershipResult(allowed = false, changed = false, owner = null)
            }
            stream.setRuntimeData(KEY_PLAYER, player.name)
            return OwnershipResult(allowed = true, changed = true, owner = player.name)
        }
        if (player == null) {
            return OwnershipResult(allowed = false, changed = false, owner = owner)
        }
        return OwnershipResult(
            allowed = owner.equals(player.name, ignoreCase = true),
            changed = false,
            owner = owner
        )
    }

    fun denyMessage(stream: ItemStream): String {
        return stream.getRuntimeData(KEY_DENY)?.toString()?.takeIf { it.isNotBlank() }
            ?: "&cThis item belongs to another player."
    }

    fun owner(stream: ItemStream): String? {
        return ownerName(stream)
    }

    fun bind(stream: ItemStream, owner: String): Boolean {
        if (!isEnabled(stream)) {
            return false
        }
        stream.setRuntimeData(KEY_PLAYER, owner)
        return true
    }

    private fun isEnabled(stream: ItemStream): Boolean {
        return asBoolean(stream.getRuntimeData(KEY_ENABLED)) ?: false
    }

    private fun isBindPlayer(stream: ItemStream): Boolean {
        return asBoolean(stream.getRuntimeData(KEY_BIND_PLAYER)) ?: false
    }

    private fun ownerName(stream: ItemStream): String? {
        return stream.getRuntimeData(KEY_PLAYER)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun asBoolean(raw: Any?): Boolean? {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.trim().equals("true", true) || raw.trim() == "1"
            else -> null
        }
    }
}
