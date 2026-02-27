package org.tabooproject.baikiruto.core.item

data class ItemScriptHooks(
    val build: String? = null,
    val drop: String? = null,
    val use: String? = null,
    val interact: String? = null,
    private val triggerEntries: Map<ItemScriptTrigger, String> = emptyMap()
) {

    private val sources: Map<ItemScriptTrigger, String> = linkedMapOf<ItemScriptTrigger, String>().apply {
        putAll(triggerEntries.filterValues { it.isNotBlank() })
        append(ItemScriptTrigger.BUILD, build)
        append(ItemScriptTrigger.DROP, drop)
        append(ItemScriptTrigger.USE, use)
        append(ItemScriptTrigger.INTERACT, interact)
    }

    fun source(trigger: ItemScriptTrigger): String? {
        return sources[trigger]
    }

    fun has(trigger: ItemScriptTrigger): Boolean {
        return !source(trigger).isNullOrBlank()
    }

    fun toScriptMap(prefix: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        sources.forEach { (trigger, source) ->
            if (source.isNotBlank()) {
                values["$prefix:${trigger.key}"] = source
            }
        }
        return values
    }

    companion object {

        fun from(raw: Map<String, String?>): ItemScriptHooks {
            val mapping = linkedMapOf<ItemScriptTrigger, String>()
            raw.forEach { (key, source) ->
                if (source.isNullOrBlank()) {
                    return@forEach
                }
                val trigger = ItemScriptTrigger.fromKey(key) ?: return@forEach
                mapping[trigger] = source
            }
            return ItemScriptHooks(triggerEntries = mapping)
        }
    }

    private fun MutableMap<ItemScriptTrigger, String>.append(trigger: ItemScriptTrigger, source: String?) {
        if (!source.isNullOrBlank()) {
            put(trigger, source)
        }
    }
}
