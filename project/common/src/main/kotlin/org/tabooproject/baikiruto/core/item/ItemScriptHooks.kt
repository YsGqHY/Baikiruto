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
    private val i18nCancelTriggerEntries: Map<String, Set<ItemScriptTrigger>> = emptyMap(),
    private val priorityTriggerEntries: Map<ItemScriptTrigger, String> = emptyMap(),
    private val i18nPriorityTriggerEntries: Map<String, Map<ItemScriptTrigger, String>> = emptyMap()
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

    private val prioritySources: Map<ItemScriptTrigger, String> =
        linkedMapOf<ItemScriptTrigger, String>().apply {
            priorityTriggerEntries.forEach { (trigger, value) ->
                BaikirutoScriptSource.normalizePriority(value)?.let { put(trigger, it) }
            }
        }

    private val localizedPrioritySources: Map<String, Map<ItemScriptTrigger, String>> =
        linkedMapOf<String, Map<ItemScriptTrigger, String>>().apply {
            i18nPriorityTriggerEntries.forEach { (locale, mapping) ->
                val normalizedLocale = normalizeLocale(locale) ?: return@forEach
                val normalizedMapping = linkedMapOf<ItemScriptTrigger, String>()
                mapping.forEach { (trigger, value) ->
                    BaikirutoScriptSource.normalizePriority(value)?.let { normalizedMapping[trigger] = it }
                }
                if (normalizedMapping.isNotEmpty()) {
                    put(normalizedLocale, normalizedMapping)
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

    /**
     * 返回该触发器配置的事件优先级（已归一化为小写，如 "lowest"/"highest"）。
     * 未配置时返回 null，调用方应回退到该触发器的默认优先级。
     * 优先匹配本地化配置，其次匹配通用配置。
     */
    fun priority(trigger: ItemScriptTrigger, locale: String? = null): String? {
        return resolveLocalizedPriority(trigger, locale) ?: prioritySources[trigger]
    }

    /**
     * 收集所有触发器（含本地化）配置的优先级集合，用于动态注册覆盖监听器。
     */
    fun configuredPriorities(): Map<ItemScriptTrigger, Set<String>> {
        val result = linkedMapOf<ItemScriptTrigger, MutableSet<String>>()
        prioritySources.forEach { (trigger, value) ->
            result.getOrPut(trigger) { linkedSetOf() } += value
        }
        localizedPrioritySources.values.forEach { mapping ->
            mapping.forEach { (trigger, value) ->
                result.getOrPut(trigger) { linkedSetOf() } += value
            }
        }
        return result
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
            val priorityMapping = linkedMapOf<ItemScriptTrigger, String>()
            for ((key, source) in raw) {
                val parsed = parseTriggerEntry(key) ?: continue
                if (parsed.cancelEvent) {
                    cancelMapping += parsed.trigger
                }
                // 优先级解析顺序：key 后缀 @priority 优先，其次子映射 priority 字段
                resolvePriority(parsed.priority, source)?.let { priorityMapping[parsed.trigger] = it }
                if (source == null || source.content.isBlank()) {
                    continue
                }
                mapping[parsed.trigger] = source
            }
            val i18nMapping = linkedMapOf<String, Map<ItemScriptTrigger, BaikirutoScriptSource>>()
            val i18nCancelMapping = linkedMapOf<String, Set<ItemScriptTrigger>>()
            val i18nPriorityMapping = linkedMapOf<String, Map<ItemScriptTrigger, String>>()
            for ((locale, scripts) in i18nRaw) {
                val normalizedLocale = normalizeLocale(locale) ?: continue
                val localized = linkedMapOf<ItemScriptTrigger, BaikirutoScriptSource>()
                val localizedCancel = linkedSetOf<ItemScriptTrigger>()
                val localizedPriority = linkedMapOf<ItemScriptTrigger, String>()
                for ((key, source) in scripts) {
                    val parsed = parseTriggerEntry(key) ?: continue
                    if (parsed.cancelEvent) {
                        localizedCancel += parsed.trigger
                    }
                    resolvePriority(parsed.priority, source)?.let { localizedPriority[parsed.trigger] = it }
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
                if (localizedPriority.isNotEmpty()) {
                    i18nPriorityMapping[normalizedLocale] = localizedPriority
                }
            }
            return ItemScriptHooks(
                triggerEntries = mapping,
                i18nTriggerEntries = i18nMapping,
                cancelTriggerEntries = cancelMapping,
                i18nCancelTriggerEntries = i18nCancelMapping,
                priorityTriggerEntries = priorityMapping,
                i18nPriorityTriggerEntries = i18nPriorityMapping
            )
        }

        private fun resolvePriority(keyPriority: String?, source: BaikirutoScriptSource?): String? {
            return BaikirutoScriptSource.normalizePriority(keyPriority)
                ?: source?.priority?.let { BaikirutoScriptSource.normalizePriority(it) }
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
            // !! 取消后缀可出现在 @priority 之前或之后，统一剥离
            val cancelEvent = source.contains("!!")
            var working = source.replace("!!", "").trim()
            // 解析 @priority 后缀，例如 on_interact@highest
            val atIndex = working.indexOf('@')
            val priority: String?
            if (atIndex >= 0) {
                priority = working.substring(atIndex + 1).trim().takeIf { it.isNotEmpty() }
                working = working.substring(0, atIndex).trim()
            } else {
                priority = null
            }
            val trigger = ItemScriptTrigger.fromKey(working) ?: return null
            return ParsedTriggerEntry(trigger, cancelEvent, priority)
        }

        private data class ParsedTriggerEntry(
            val trigger: ItemScriptTrigger,
            val cancelEvent: Boolean,
            val priority: String? = null
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

    private fun resolveLocalizedPriority(trigger: ItemScriptTrigger, locale: String?): String? {
        val normalized = normalizeLocale(locale) ?: return null
        val languageOnly = normalized.substringBefore('_')
        return localizedPrioritySources[normalized]?.get(trigger)
            ?: localizedPrioritySources[languageOnly]?.get(trigger)
    }

    private fun MutableMap<ItemScriptTrigger, BaikirutoScriptSource>.append(trigger: ItemScriptTrigger, source: String?) {
        BaikirutoScriptSource.of(source)?.let { put(trigger, it) }
    }

    private fun normalizeLocale(value: String?): String? {
        return Companion.normalizeLocale(value)
    }
}
