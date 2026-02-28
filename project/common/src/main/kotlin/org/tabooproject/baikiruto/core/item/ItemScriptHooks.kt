package org.tabooproject.baikiruto.core.item

data class ItemScriptHooks(
    val build: String? = null,
    val drop: String? = null,
    val use: String? = null,
    val interact: String? = null,
    private val triggerEntries: Map<ItemScriptTrigger, String> = emptyMap(),
    private val i18nTriggerEntries: Map<String, Map<ItemScriptTrigger, String>> = emptyMap()
) {

    private val sources: Map<ItemScriptTrigger, String> = linkedMapOf<ItemScriptTrigger, String>().apply {
        putAll(triggerEntries.filterValues { it.isNotBlank() })
        append(ItemScriptTrigger.BUILD, build)
        append(ItemScriptTrigger.DROP, drop)
        append(ItemScriptTrigger.USE, use)
        append(ItemScriptTrigger.INTERACT, interact)
    }

    private val localizedSources: Map<String, Map<ItemScriptTrigger, String>> =
        linkedMapOf<String, Map<ItemScriptTrigger, String>>().apply {
            i18nTriggerEntries.forEach { (locale, mapping) ->
                val normalizedLocale = normalizeLocale(locale) ?: return@forEach
                val normalizedMapping = mapping.filterValues { it.isNotBlank() }
                if (normalizedMapping.isNotEmpty()) {
                    put(normalizedLocale, normalizedMapping)
                }
            }
        }

    fun source(trigger: ItemScriptTrigger, locale: String? = null): String? {
        return resolveLocalizedSource(trigger, locale) ?: sources[trigger]
    }

    fun has(trigger: ItemScriptTrigger, locale: String? = null): Boolean {
        return !source(trigger, locale).isNullOrBlank()
    }

    fun toScriptMap(prefix: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        sources.forEach { (trigger, source) ->
            if (source.isNotBlank()) {
                values["$prefix:${trigger.key}"] = source
            }
        }
        localizedSources.forEach { (locale, scripts) ->
            scripts.forEach { (trigger, source) ->
                if (source.isNotBlank()) {
                    values["$prefix:i18n:$locale:${trigger.key}"] = source
                }
            }
        }
        return values
    }

    companion object {

        fun from(
            raw: Map<String, String?>,
            i18nRaw: Map<String, Map<String, String?>> = emptyMap()
        ): ItemScriptHooks {
            val mapping = linkedMapOf<ItemScriptTrigger, String>()
            for ((key, source) in raw) {
                if (source.isNullOrBlank()) {
                    continue
                }
                val trigger = ItemScriptTrigger.fromKey(key) ?: continue
                mapping[trigger] = source
            }
            val i18nMapping = linkedMapOf<String, Map<ItemScriptTrigger, String>>()
            for ((locale, scripts) in i18nRaw) {
                val normalizedLocale = normalizeLocale(locale) ?: continue
                val localized = linkedMapOf<ItemScriptTrigger, String>()
                for ((key, source) in scripts) {
                    if (source.isNullOrBlank()) {
                        continue
                    }
                    val trigger = ItemScriptTrigger.fromKey(key) ?: continue
                    localized[trigger] = source
                }
                if (localized.isNotEmpty()) {
                    i18nMapping[normalizedLocale] = localized
                }
            }
            return ItemScriptHooks(triggerEntries = mapping, i18nTriggerEntries = i18nMapping)
        }

        private fun normalizeLocale(value: String?): String? {
            return value
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.replace('-', '_')
                ?.lowercase()
        }
    }

    private fun resolveLocalizedSource(trigger: ItemScriptTrigger, locale: String?): String? {
        val normalized = normalizeLocale(locale) ?: return null
        val languageOnly = normalized.substringBefore('_')
        return localizedSources[normalized]?.get(trigger)
            ?: localizedSources[languageOnly]?.get(trigger)
    }

    private fun MutableMap<ItemScriptTrigger, String>.append(trigger: ItemScriptTrigger, source: String?) {
        if (!source.isNullOrBlank()) {
            put(trigger, source)
        }
    }

    private fun normalizeLocale(value: String?): String? {
        return Companion.normalizeLocale(value)
    }
}
