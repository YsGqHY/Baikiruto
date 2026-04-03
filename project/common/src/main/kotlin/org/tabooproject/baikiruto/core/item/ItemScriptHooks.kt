package org.tabooproject.baikiruto.core.item

import org.tabooproject.baikiruto.core.BaikirutoScriptSource

data class ItemScriptHooks(
    val build: String? = null,
    val drop: String? = null,
    val use: String? = null,
    val interact: String? = null,
    private val triggerEntries: Map<ItemScriptTrigger, BaikirutoScriptSource> = emptyMap(),
    private val i18nTriggerEntries: Map<String, Map<ItemScriptTrigger, BaikirutoScriptSource>> = emptyMap(),
    private val cancelTriggerEntries: Set<ItemScriptTrigger> = emptySet(),
    private val i18nCancelTriggerEntries: Map<String, Set<ItemScriptTrigger>> = emptyMap()
) {

    private val sources: Map<ItemScriptTrigger, BaikirutoScriptSource> = linkedMapOf<ItemScriptTrigger, BaikirutoScriptSource>().apply {
        putAll(triggerEntries.filterValues { it.content.isNotBlank() })
        append(ItemScriptTrigger.BUILD, build)
        append(ItemScriptTrigger.DROP, drop)
        append(ItemScriptTrigger.USE, use)
        append(ItemScriptTrigger.INTERACT, interact)
    }

    private val localizedSources: Map<String, Map<ItemScriptTrigger, BaikirutoScriptSource>> =
        linkedMapOf<String, Map<ItemScriptTrigger, BaikirutoScriptSource>>().apply {
            i18nTriggerEntries.forEach { (locale, mapping) ->
                val normalizedLocale = normalizeLocale(locale) ?: return@forEach
                val normalizedMapping = mapping.filterValues { it.content.isNotBlank() }
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

    fun entry(trigger: ItemScriptTrigger, locale: String? = null): BaikirutoScriptSource? {
        return resolveLocalizedEntry(trigger, locale) ?: sources[trigger]
    }

    fun source(trigger: ItemScriptTrigger, locale: String? = null): String? {
        return entry(trigger, locale)?.content
    }

    fun type(trigger: ItemScriptTrigger, locale: String? = null): String? {
        return entry(trigger, locale)?.normalizedType()
    }

    fun has(trigger: ItemScriptTrigger, locale: String? = null): Boolean {
        return entry(trigger, locale) != null
    }

    fun shouldCancel(trigger: ItemScriptTrigger, locale: String? = null): Boolean {
        if (resolveLocalizedCancel(locale)?.contains(trigger) == true) {
            return true
        }
        return trigger in cancelSources
    }

    fun toScriptMap(prefix: String): Map<String, String> {
        return toTypedScriptMap(prefix).mapValues { (_, source) -> source.content }
    }

    fun toTypedScriptMap(prefix: String): Map<String, BaikirutoScriptSource> {
        return linkedMapOf<String, BaikirutoScriptSource>().apply {
            sources.forEach { (trigger, source) ->
                if (source.content.isNotBlank()) {
                    put("$prefix:${trigger.key}", source)
                }
            }
            localizedSources.forEach { (locale, scripts) ->
                scripts.forEach { (trigger, source) ->
                    if (source.content.isNotBlank()) {
                        put("$prefix:i18n:$locale:${trigger.key}", source)
                    }
                }
            }
        }
    }

    companion object {

        fun from(
            raw: Map<String, String?>,
            i18nRaw: Map<String, Map<String, String?>> = emptyMap()
        ): ItemScriptHooks {
            return fromSources(
                raw = raw.mapValues { (_, source) -> BaikirutoScriptSource.of(source) },
                i18nRaw = i18nRaw.mapValues { (_, scripts) ->
                    scripts.mapValues { (_, source) -> BaikirutoScriptSource.of(source) }
                }
            )
        }

        fun fromSources(
            raw: Map<String, BaikirutoScriptSource?>,
            i18nRaw: Map<String, Map<String, BaikirutoScriptSource?>> = emptyMap()
        ): ItemScriptHooks {
            val mapping = linkedMapOf<ItemScriptTrigger, BaikirutoScriptSource>()
            val cancelMapping = linkedSetOf<ItemScriptTrigger>()
            for ((key, source) in raw) {
                val parsed = parseTriggerEntry(key) ?: continue
                if (parsed.cancelEvent) {
                    cancelMapping += parsed.trigger
                }
                if (source == null || source.content.isBlank()) {
                    continue
                }
                mapping[parsed.trigger] = source
            }
            val i18nMapping = linkedMapOf<String, Map<ItemScriptTrigger, BaikirutoScriptSource>>()
            val i18nCancelMapping = linkedMapOf<String, Set<ItemScriptTrigger>>()
            for ((locale, scripts) in i18nRaw) {
                val normalizedLocale = normalizeLocale(locale) ?: continue
                val localized = linkedMapOf<ItemScriptTrigger, BaikirutoScriptSource>()
                val localizedCancel = linkedSetOf<ItemScriptTrigger>()
                for ((key, source) in scripts) {
                    val parsed = parseTriggerEntry(key) ?: continue
                    if (parsed.cancelEvent) {
                        localizedCancel += parsed.trigger
                    }
                    if (source == null || source.content.isBlank()) {
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

    private fun resolveLocalizedEntry(trigger: ItemScriptTrigger, locale: String?): BaikirutoScriptSource? {
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

    private fun MutableMap<ItemScriptTrigger, BaikirutoScriptSource>.append(trigger: ItemScriptTrigger, source: String?) {
        BaikirutoScriptSource.of(source)?.let { put(trigger, it) }
    }

    private fun normalizeLocale(value: String?): String? {
        return Companion.normalizeLocale(value)
    }
}
