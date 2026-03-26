package org.tabooproject.baikiruto.impl.item

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.tabooproject.baikiruto.impl.BaikirutoSettings

object LegacyTextColorizer {

    private var miniMessageTransformerOverride: ((String) -> String?)? = null

    @Volatile
    private var miniMessageEnabledOverride: Boolean? = null

    @Volatile
    private var miniMessageAvailabilityOverride: Boolean? = null

    fun colorize(source: String): String {
        val normalized = preprocessMiniMessage(source)
        if ('&' !in normalized) {
            return normalized
        }
        val chars = normalized.toCharArray()
        for (index in 0 until chars.size - 1) {
            val marker = chars[index]
            val code = chars[index + 1]
            if (marker == '&' && code in LEGACY_COLOR_CODES) {
                chars[index] = '§'
                chars[index + 1] = code.lowercaseChar()
            }
        }
        return String(chars)
    }

    fun colorize(lines: List<String>): List<String> {
        return lines.map(::colorize)
    }

    fun miniMessageAvailable(): Boolean {
        return miniMessageAvailabilityOverride ?: MiniMessageBridge.available
    }

    internal fun setMiniMessageEnabledOverride(value: Boolean?) {
        miniMessageEnabledOverride = value
    }

    internal fun setMiniMessageAvailabilityOverride(value: Boolean?) {
        miniMessageAvailabilityOverride = value
    }

    internal fun setMiniMessageTransformerOverride(transformer: ((String) -> String?)?) {
        miniMessageTransformerOverride = transformer
    }

    internal fun clearMiniMessageOverrides() {
        miniMessageTransformerOverride = null
        miniMessageEnabledOverride = null
        miniMessageAvailabilityOverride = null
    }

    private fun miniMessageEnabled(): Boolean {
        return miniMessageEnabledOverride ?: BaikirutoSettings.miniMessageEnabled
    }

    private fun preprocessMiniMessage(source: String): String {
        if ('<' !in source || !miniMessageEnabled() || !miniMessageAvailable()) {
            return source
        }
        val transformed = miniMessageTransformerOverride?.invoke(source)
            ?: MiniMessageBridge.serializeToLegacy(source)
        return transformed ?: source
    }

    /**
     * 隔离 adventure 类引用，避免在没有 adventure 的服务端上触发 [NoClassDefFoundError]。
     * 整个 object 只有在 [available] 为 true 时才会被访问内部方法。
     */
    private object MiniMessageBridge {

        val available: Boolean = runCatching {
            Class.forName("net.kyori.adventure.text.minimessage.MiniMessage")
            Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")
            true
        }.getOrDefault(false)

        fun serializeToLegacy(source: String): String? {
            if (!available) return null
            return runCatching {
                val component = MiniMessage.miniMessage().deserialize(source)
                LegacyComponentSerializer.legacySection().serialize(component)
            }.getOrNull()
        }
    }

    private const val LEGACY_COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx"
}
