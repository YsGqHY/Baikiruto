package org.tabooproject.baikiruto.impl.item.feature

import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.item.DefaultItemStream
import taboolib.common.platform.function.console
import taboolib.common.platform.function.info
import taboolib.module.lang.asLangText
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
        val enabled = isEnabled(stream)
        val bindPlayer = isBindPlayer(stream)
        BaikirutoSettings.debug {
            info("[Baikiruto][DEBUG][UNIQUE_PREPARE] item=${stream.itemId} enabled=$enabled bindPlayer=$bindPlayer player=${player?.name} existingOwner=${ownerName(stream)} existingUuid=${stream.getRuntimeData(KEY_UUID)}")
        }
        if (!enabled) {
            return
        }
        if (stream.getRuntimeData(KEY_UUID) == null) {
            val uuid = UUID.randomUUID().toString()
            stream.setRuntimeData(KEY_UUID, uuid)
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_PREPARE] item=${stream.itemId} generated uuid=$uuid") }
        }
        if (stream.getRuntimeData(KEY_DATE) == null) {
            stream.setRuntimeData(KEY_DATE, System.currentTimeMillis())
        }
        if (bindPlayer && player != null) {
            val existingOwner = ownerName(stream)
            if (existingOwner == null) {
                stream.setRuntimeData(KEY_PLAYER, player.name)
                BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_PREPARE] item=${stream.itemId} first bind -> owner=${player.name}") }
            } else {
                BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_PREPARE] item=${stream.itemId} already bound to=$existingOwner, skip overwrite") }
            }
        }
    }

    fun checkOwnership(stream: ItemStream, player: Player?): OwnershipResult {
        val enabled = isEnabled(stream)
        val bindPlayer = isBindPlayer(stream)
        val currentOwner = owner(stream)
        BaikirutoSettings.debug {
            info("[Baikiruto][DEBUG][UNIQUE_CHECK] item=${stream.itemId} enabled=$enabled bindPlayer=$bindPlayer owner=$currentOwner player=${player?.name}")
        }
        if (!enabled) {
            return OwnershipResult(allowed = true, changed = false, owner = null)
        }
        if (!bindPlayer) {
            return OwnershipResult(allowed = true, changed = false, owner = currentOwner)
        }
        val owner = currentOwner
        if (owner.isNullOrBlank()) {
            if (player == null) {
                BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_CHECK] item=${stream.itemId} -> DENIED (no owner, no player)") }
                return OwnershipResult(allowed = false, changed = false, owner = null)
            }
            stream.setRuntimeData(KEY_PLAYER, player.name)
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_CHECK] item=${stream.itemId} -> auto-bind to ${player.name}") }
            return OwnershipResult(allowed = true, changed = true, owner = player.name)
        }
        if (player == null) {
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_CHECK] item=${stream.itemId} -> DENIED (owner=$owner, no player)") }
            return OwnershipResult(allowed = false, changed = false, owner = owner)
        }
        val allowed = owner.equals(player.name, ignoreCase = true)
        BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_CHECK] item=${stream.itemId} -> allowed=$allowed (owner=$owner, player=${player.name})") }
        return OwnershipResult(
            allowed = allowed,
            changed = false,
            owner = owner
        )
    }

    fun customDenyMessage(stream: ItemStream): String? {
        return stream.getRuntimeData(KEY_DENY)?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * 获取拒绝消息。优先使用物品 runtimeData 中的自定义消息，
     * 回退到 lang 系统的 "item-unique-deny" key。
     */
    fun denyMessage(stream: ItemStream): String {
        return customDenyMessage(stream)
            ?: console().asLangText("item-unique-deny")
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
        val raw = stream.getRuntimeData(KEY_ENABLED)
        val result = asBoolean(raw) ?: false
        if (raw == null) {
            BaikirutoSettings.debug { info("[Baikiruto][DEBUG][UNIQUE_ENABLED] item=${stream.itemId} raw=null -> false (key '$KEY_ENABLED' not in runtimeData)") }
        }
        return result
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
