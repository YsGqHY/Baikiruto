package org.tabooproject.baikiruto.core

data class BaikirutoScriptSource(
    val type: String = DEFAULT_TYPE,
    val content: String
) {

    init {
        require(content.isNotBlank()) { "Script content cannot be blank." }
    }

    fun normalizedType(): String {
        return normalizeType(type)
    }

    fun asRuntimeMap(): Map<String, String> {
        return linkedMapOf(
            "type" to normalizedType(),
            "source" to content
        )
    }

    companion object {

        const val DEFAULT_TYPE: String = "fluxon"

        fun of(content: String?, type: String = DEFAULT_TYPE): BaikirutoScriptSource? {
            val normalizedContent = content?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return BaikirutoScriptSource(type = normalizeType(type), content = normalizedContent)
        }

        fun fromRuntimeValue(source: Any?): BaikirutoScriptSource? {
            return when (source) {
                null -> null
                is BaikirutoScriptSource -> of(source.content, source.type)
                is String -> of(source)
                is Iterable<*> -> of(source.mapNotNull { it?.toString() }.joinToString("\n"))
                is Map<*, *> -> {
                    val type = source["type"]?.toString()
                        ?: source["engine"]?.toString()
                        ?: DEFAULT_TYPE
                    val content = source["script"]
                        ?: source["source"]
                        ?: source["content"]
                    val parsedContent = fromRuntimeValue(content)?.content
                    of(parsedContent, type)
                }
                else -> of(source.toString())
            }
        }

        fun normalizeType(source: String?): String {
            return source
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.replace('-', '_')
                ?.lowercase()
                ?: DEFAULT_TYPE
        }
    }
}
