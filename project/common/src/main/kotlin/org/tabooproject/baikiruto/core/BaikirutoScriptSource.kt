package org.tabooproject.baikiruto.core

data class BaikirutoScriptSource(
    val type: String = DEFAULT_TYPE,
    val content: String,
    val priority: String? = null
) {

    init {
        require(content.isNotBlank()) { "Script content cannot be blank." }
    }

    fun normalizedType(): String {
        return normalizeType(type)
    }

    fun asRuntimeMap(): Map<String, String> {
        val map = linkedMapOf(
            "type" to normalizedType(),
            "source" to content
        )
        priority?.let { map["priority"] = it }
        return map
    }

    companion object {

        const val DEFAULT_TYPE: String = "fluxon"

        fun of(content: String?, type: String = DEFAULT_TYPE, priority: String? = null): BaikirutoScriptSource? {
            val normalizedContent = content?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return BaikirutoScriptSource(
                type = normalizeType(type),
                content = normalizedContent,
                priority = normalizePriority(priority)
            )
        }

        fun fromRuntimeValue(source: Any?): BaikirutoScriptSource? {
            return when (source) {
                null -> null
                is BaikirutoScriptSource -> of(source.content, source.type, source.priority)
                is String -> of(source)
                is Iterable<*> -> of(source.mapNotNull { it?.toString() }.joinToString("\n"))
                is Map<*, *> -> {
                    val type = source["type"]?.toString()
                        ?: source["engine"]?.toString()
                        ?: DEFAULT_TYPE
                    val priority = source["priority"]?.toString()
                    val content = source["script"]
                        ?: source["source"]
                        ?: source["content"]
                    val parsedContent = fromRuntimeValue(content)?.content
                    of(parsedContent, type, priority)
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

        /**
         * 归一化事件优先级配置：去空白、连字符转下划线、转小写。
         * 空值或空字符串返回 null（表示使用默认优先级）。
         */
        fun normalizePriority(source: String?): String? {
            return source
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.replace('-', '_')
                ?.lowercase()
        }
    }
}
