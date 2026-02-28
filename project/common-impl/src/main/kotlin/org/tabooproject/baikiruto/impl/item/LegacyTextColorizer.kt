package org.tabooproject.baikiruto.impl.item

object LegacyTextColorizer {

    fun colorize(source: String): String {
        if ('&' !in source) {
            return source
        }
        val chars = source.toCharArray()
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

    private const val LEGACY_COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx"
}
