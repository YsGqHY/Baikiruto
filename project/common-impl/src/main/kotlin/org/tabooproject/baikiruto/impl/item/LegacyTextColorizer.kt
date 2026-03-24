package org.tabooproject.baikiruto.impl.item

import org.tabooproject.baikiruto.impl.BaikirutoSettings
import java.lang.reflect.Modifier

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
        return miniMessageAvailabilityOverride ?: MiniMessageRuntime.isAvailable()
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
            ?: MiniMessageRuntime.serializeToLegacy(source)
        return transformed ?: source
    }

    private object MiniMessageRuntime {

        private val miniMessageClass by lazy {
            resolveClass("net.kyori.adventure.text.minimessage.MiniMessage")
        }

        private val legacySerializerClass by lazy {
            resolveClass("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer")
        }

        private val miniMessageFactory by lazy {
            miniMessageClass?.methods?.firstOrNull { method ->
                method.name == "miniMessage" &&
                    method.parameterCount == 0 &&
                    Modifier.isStatic(method.modifiers)
            }
        }

        private val legacySectionFactory by lazy {
            legacySerializerClass?.methods?.firstOrNull { method ->
                method.name == "legacySection" &&
                    method.parameterCount == 0 &&
                    Modifier.isStatic(method.modifiers)
            }
        }

        fun isAvailable(): Boolean {
            return miniMessageFactory != null && legacySectionFactory != null
        }

        fun serializeToLegacy(source: String): String? {
            val miniMessage = runCatching { miniMessageFactory?.invoke(null) }.getOrNull() ?: return null
            val deserialize = miniMessage.javaClass.methods.firstOrNull { method ->
                method.name == "deserialize" &&
                    method.parameterCount == 1 &&
                    method.parameterTypes[0] == String::class.java
            } ?: return null
            val component = runCatching { deserialize.invoke(miniMessage, source) }.getOrNull() ?: return null
            val serializer = runCatching { legacySectionFactory?.invoke(null) }.getOrNull() ?: return null
            val serialize = serializer.javaClass.methods.firstOrNull { method ->
                method.name == "serialize" && method.parameterCount == 1
            } ?: return null
            return runCatching { serialize.invoke(serializer, component) as? String }.getOrNull()
        }

        private fun resolveClass(name: String): Class<*>? {
            return runCatching { Class.forName(name, false, javaClass.classLoader) }.getOrNull()
        }
    }

    private const val LEGACY_COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx"
}
