package org.tabooproject.baikiruto.core.item

data class ItemScriptHooks(
    val build: String? = null,
    val drop: String? = null,
    val use: String? = null,
    val interact: String? = null,
    private val triggerEntries: Map<ItemScriptTrigger, String> = emptyMap(),
    private val i18nTriggerEntries: Map<String, Map<ItemScriptTrigger, String>> = emptyMap(),
    private val cancelTriggerEntries: Set<ItemScriptTrigger> = emptySet(),
    private val i18nCancelTriggerEntries: Map<String, Set<ItemScriptTrigger>> = emptyMap()
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

    private val cancelSources: Set<ItemScriptTrigger> = cancelTriggerEntries.toSet()

    private val localizedCancelSources: Map<String, Set<ItemScriptTrigger>> =
        linkedMapOf<String, Set<ItemScriptTrigger>>().apply {
            i18nCancelTriggerEntries.forEach { (locale, entries) ->
                val normalizedLocale = normalizeLocale(locale) ?: return@forEach
                val normalizedEntries = entries.filterNotNull().toSet()
                if (normalizedEntries.isNotEmpty()) {
                    put(normalizedLocale, normalizedEntries)
                }
            }
        }

    fun source(trigger: ItemScriptTrigger, locale: String? = null): String? {
        return resolveLocalizedSource(trigger, locale) ?: sources[trigger]
    }

    fun has(trigger: ItemScriptTrigger, locale: String? = null): Boolean {
        return !source(trigger, locale).isNullOrBlank()
    }

    fun shouldCancel(trigger: ItemScriptTrigger, locale: String? = null): Boolean {
        if (resolveLocalizedCancel(locale)?.contains(trigger) == true) {
            return true
        }
        return trigger in cancelSources
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
            val cancelMapping = linkedSetOf<ItemScriptTrigger>()
            for ((key, source) in raw) {
                val parsed = parseTriggerEntry(key) ?: continue
                if (parsed.cancelEvent) {
                    cancelMapping += parsed.trigger
                }
                if (source.isNullOrBlank()) {
                    continue
                }
                mapping[parsed.trigger] = source
            }
            val i18nMapping = linkedMapOf<String, Map<ItemScriptTrigger, String>>()
            val i18nCancelMapping = linkedMapOf<String, Set<ItemScriptTrigger>>()
            for ((locale, scripts) in i18nRaw) {
                val normalizedLocale = normalizeLocale(locale) ?: continue
                val localized = linkedMapOf<ItemScriptTrigger, String>()
                val localizedCancel = linkedSetOf<ItemScriptTrigger>()
                for ((key, source) in scripts) {
                    val parsed = parseTriggerEntry(key) ?: continue
                    if (parsed.cancelEvent) {
                        localizedCancel += parsed.trigger
                    }
                    if (source.isNullOrBlank()) {
                        continue
                    }
                    localized[parsed.trigger] = source
                }
                if (localized.isNotEmpty()) {
                    i18nMapping[normalizedLocale] = localized
                }
                if (localizedCancel.isNotEmpty()) {
                    i18nCancelMapping[normalizedLocale] = localizedCancel
                }
            }
            return ItemScriptHooks(
                triggerEntries = mapping,
                i18nTriggerEntries = i18nMapping,
                cancelTriggerEntries = cancelMapping,
                i18nCancelTriggerEntries = i18nCancelMapping
            )
        }

        private fun normalizeLocale(value: String?): String? {
            return value
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.replace('-', '_')
                ?.lowercase()
        }

        private fun parseTriggerEntry(rawKey: String): ParsedTriggerEntry? {
            val source = rawKey.trim()
            if (source.isEmpty()) {
                return null
            }
            val cancelEvent = source.endsWith("!!")
            val normalizedKey = if (cancelEvent) {
                source.dropLast(2).trim()
            } else {
                source
            }
            val trigger = ItemScriptTrigger.fromKey(normalizedKey) ?: return null
            return ParsedTriggerEntry(trigger, cancelEvent)
        }

        private data class ParsedTriggerEntry(
            val trigger: ItemScriptTrigger,
            val cancelEvent: Boolean
        )
    }

    private fun resolveLocalizedSource(trigger: ItemScriptTrigger, locale: String?): String? {
        val normalized = normalizeLocale(locale) ?: return null
        val languageOnly = normalized.substringBefore('_')
        return localizedSources[normalized]?.get(trigger)
            ?: localizedSources[languageOnly]?.get(trigger)
    }

    private fun resolveLocalizedCancel(locale: String?): Set<ItemScriptTrigger>? {
        val normalized = normalizeLocale(locale) ?: return null
        val languageOnly = normalized.substringBefore('_')
        return localizedCancelSources[normalized]
            ?: localizedCancelSources[languageOnly]
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
