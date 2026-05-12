package org.tabooproject.baikiruto.impl.item.feature

import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.impl.item.LegacyTextColorizer
import java.util.Locale

object ItemDropEntityFeature {

    const val KEY_DISPLAY_NAME = "drop-display-name"
    const val KEY_DISPLAY_VISIBLE = "drop-display-visible"

    fun hasDropDisplay(stream: ItemStream): Boolean {
        return rawDisplayName(stream) != null
    }

    fun resolveDisplayName(stream: ItemStream): String? {
        return rawDisplayName(stream)?.let(LegacyTextColorizer::colorize)
    }

    fun resolveDisplayVisible(stream: ItemStream): Boolean {
        return asBoolean(stream.getRuntimeData(KEY_DISPLAY_VISIBLE)) ?: true
    }

    fun apply(entity: org.bukkit.entity.Item, stream: ItemStream) {
        val displayName = resolveDisplayName(stream) ?: return
        entity.customName = displayName
        entity.isCustomNameVisible = resolveDisplayVisible(stream)
    }

    private fun rawDisplayName(stream: ItemStream): String? {
        return stream.getRuntimeData(KEY_DISPLAY_NAME)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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
