package org.tabooproject.baikiruto.impl.item

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.ItemSignal
import org.tabooproject.baikiruto.core.item.ItemStream
import org.tabooproject.baikiruto.core.item.ItemStreamData
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.event.ItemReleaseDisplayEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseFinalEvent
import org.tabooproject.baikiruto.core.item.event.ItemReleaseDisplayBuildEvent
import org.tabooproject.baikiruto.core.item.event.ItemSelectDisplayEvent
import org.tabooproject.baikiruto.impl.item.feature.ItemCooldownFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemDataMapperFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemDurabilityFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemNativeFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemUniqueFeature
import org.tabooproject.baikiruto.impl.hook.HeadDatabaseHook
import org.tabooproject.baikiruto.impl.version.VersionAdapterService
import taboolib.platform.compat.replacePlaceholder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DefaultItemStream(
    private var backingItem: ItemStack,
    override val itemId: String,
    override val versionHash: String,
    initialRuntimeData: Map<String, Any?> = emptyMap(),
    initialMetaHistory: List<String> = emptyList()
) : ItemStream {

    private val runtimeDataBacking = LinkedHashMap(initialRuntimeData)
    private val lockedRuntimePaths = linkedSetOf<String>()
    private val lockedDisplayFields = linkedSetOf<String>()
    private val lockedIconMaterial = backingItem.type
    private val metaHistoryBacking = CopyOnWriteArrayList<String>()
    private val signalBacking = ConcurrentHashMap.newKeySet<ItemSignal>()
    private var locked = false
    private var invocationContext: Map<String, Any?> = emptyMap()

    init {
        mergeLockedRuntimePaths(runtimeDataBacking[LOCKED_DATA_PATHS_KEY])
        if (lockedRuntimePaths.isNotEmpty()) {
            runtimeDataBacking[LOCKED_DATA_PATHS_KEY] = lockedRuntimePaths.toList()
        }
        mergeLockedDisplayFields(runtimeDataBacking[LOCKED_DISPLAY_FIELDS_KEY])
        if (lockedDisplayFields.isNotEmpty()) {
            runtimeDataBacking[LOCKED_DISPLAY_FIELDS_KEY] = lockedDisplayFields.toList()
        }
        metaHistoryBacking += initialMetaHistory
    }

    override val metaHistory: List<String>
        get() = metaHistoryBacking.toList()

    override val runtimeData: Map<String, Any?>
        get() = runtimeDataBacking.toMap()

    override val signals: Set<ItemSignal>
        get() = signalBacking.toSet()

    override fun itemStack(): ItemStack {
        return backingItem
    }

    override fun snapshot(): ItemStack {
        return backingItem.clone()
    }

    override fun setDisplayName(name: String?): ItemStream {
        ensureUnlocked("setDisplayName")
        if (isDisplayFieldLocked("name")) {
            return this
        }
        VersionAdapterService.applyDisplayName(backingItem, name)
        return this
    }

    override fun setLore(lines: List<String>): ItemStream {
        ensureUnlocked("setLore")
        if (isDisplayFieldLocked("lore")) {
            return this
        }
        VersionAdapterService.applyLore(backingItem, lines)
        return this
    }

    override fun setRuntimeData(key: String, value: Any?): ItemStream {
        ensureUnlocked("setRuntimeData")
        putRuntimeData(key, value)
        return this
    }

    override fun getRuntimeData(key: String): Any? {
        return runtimeDataBacking[key]
    }

    override fun markSignal(signal: ItemSignal): ItemStream {
        ensureUnlocked("markSignal")
        signalBacking += signal
        return this
    }

    override fun hasSignal(signal: ItemSignal): Boolean {
        return signal in signalBacking
    }

    override fun applyMeta(meta: Meta): ItemStream {
        ensureUnlocked("applyMeta")
        meta.build(this)
        metaHistoryBacking += meta.id
        return this
    }

    override fun isVanilla(): Boolean {
        return runCatching { Baikiruto.api().getItem(itemId) }
            .getOrNull() == null
    }

    override fun isOutdated(): Boolean {
        val item = runCatching { Baikiruto.api().getItem(itemId) }.getOrNull() ?: return false
        val latestHash = if (item is DefaultItem) {
            item.latestVersionHash()
        } else {
            item.build().versionHash
        }
        return latestHash != versionHash
    }

    override fun rebuild(player: Player?): ItemStream {
        val item = runCatching { Baikiruto.api().getItem(itemId) }.getOrNull() ?: return this
        val context = LinkedHashMap<String, Any?>().apply {
            putAll(invocationContext)
            val resolvedPlayer = player ?: invocationContext["player"] as? Player ?: invocationContext["sender"] as? Player
            put("player", resolvedPlayer)
            putIfAbsent("sender", resolvedPlayer)
            put("ctx", LinkedHashMap(runtimeDataBacking))
        }
        val rebuilt = item.build(context)
        if (rebuilt is DefaultItemStream) {
            rebuilt.copyRuntimeDataFrom(runtimeDataBacking)
        } else {
            runtimeDataBacking.forEach { (key, value) ->
                rebuilt.setRuntimeData(key, value)
            }
        }
        signalBacking.forEach { signal ->
            rebuilt.markSignal(signal)
        }
        if (rebuilt is DefaultItemStream) {
            rebuilt.rememberInvocationContext(context)
            rebuilt.lock(locked)
        }
        return rebuilt
    }

    override fun rebuildToItemStack(player: Player?): ItemStack {
        return rebuild(player).toItemStack()
    }

    override fun lock(value: Boolean) {
        locked = value
    }

    override fun isLocked(): Boolean {
        return locked
    }

    override fun snapshotData(): ItemStreamData {
        val mergedRuntimeData = LinkedHashMap(runtimeData)
        mergedRuntimeData["versionData"] = VersionAdapterService.readItemData(backingItem)
        return ItemStreamData(
            itemId = itemId,
            versionHash = versionHash,
            metaHistory = metaHistory,
            runtimeData = mergedRuntimeData
        )
    }

    override fun toItemStack(): ItemStack {
        if (locked) {
            return backingItem.clone()
        }
        val context = buildInvocationContext()
        val player = context["player"] as? Player
        ItemUniqueFeature.prepare(this, player)
        val contextMutable = LinkedHashMap(context)
        ItemDataMapperFeature.apply(this, context)
        val releaseEvent = ItemReleaseEvent(
            stream = this,
            player = player,
            source = context["event"],
            context = contextMutable,
            icon = backingItem.type,
            data = readLegacyDamage(backingItem),
            itemMeta = backingItem.itemMeta?.clone()
        )
        if (!postReleaseEvent(releaseEvent)) {
            return backingItem.clone()
        }
        applyReleaseEventMutation(releaseEvent)
        dispatchReleaseScripts(ItemScriptTrigger.RELEASE, context)
        applySelectedDisplay(player, context["event"], contextMutable)
        applyI18nDisplay()
        if (!postReleaseEvent(ItemReleaseDisplayEvent(this, player, context["event"], contextMutable))) {
            return backingItem.clone()
        }
        dispatchReleaseScripts(ItemScriptTrigger.RELEASE_DISPLAY, context)
        ItemDurabilityFeature.prepare(this)
        ItemCooldownFeature.injectDisplayData(this, player)
        applyRuntimeDisplay()
        applyPlaceholderDisplay(player)
        HeadDatabaseHook.patchSkullData(runtimeDataBacking)
        ItemStreamTransport.sync(
            itemStack = backingItem,
            itemId = itemId,
            versionHash = versionHash,
            metaHistory = metaHistoryBacking.toList(),
            runtimeData = runtimeDataBacking
        )
        VersionAdapterService.applyVersionEffects(backingItem, runtimeDataBacking)
        ItemNativeFeature.apply(backingItem, runtimeDataBacking)
        val finalEvent = ItemReleaseFinalEvent(
            stream = this,
            player = player,
            source = context["event"],
            context = contextMutable,
            itemStack = backingItem.clone()
        )
        postReleaseEvent(finalEvent)
        backingItem = finalEvent.itemStack.clone()
        return backingItem.clone()
    }

    fun rememberInvocationContext(context: Map<String, Any?>) {
        if (context.isNotEmpty()) {
            invocationContext = LinkedHashMap(context)
        }
    }

    fun syncScriptResult(
        result: Any?,
        providedItemRef: ItemStack?,
        providedItemBaseline: ItemStack?
    ) {
        ensureUnlocked("syncScriptResult")
        when (result) {
            is ItemStack -> {
                val previous = backingItem.clone()
                if (providedItemRef != null && providedItemBaseline != null && result === providedItemRef && result == providedItemBaseline) {
                    return
                }
                backingItem = result.clone()
                enforceDisplayLocks(previous)
            }
            is ItemStream -> {
                val previous = backingItem.clone()
                backingItem = result.snapshot()
                enforceDisplayLocks(previous)
                result.runtimeData.forEach { (key, value) ->
                    putRuntimeData(key, value)
                }
            }
            is Map<*, *> -> {
                result.forEach { (key, value) ->
                    val normalized = key?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
                    putRuntimeData(normalized, value)
                }
            }
        }
    }

    private fun dispatchReleaseScripts(trigger: ItemScriptTrigger, context: Map<String, Any?>) {
        val item = runCatching { Baikiruto.api().getItem(itemId) }.getOrNull() ?: return
        if (ItemScriptActionDispatcher.hasAction(item, trigger, context)) {
            ItemScriptActionDispatcher.dispatch(item, trigger, this, context)
        }
    }

    private fun postReleaseEvent(event: org.tabooproject.baikiruto.core.item.event.ItemLifecycleEvent): Boolean {
        Baikiruto.api().getItemEventBus().post(event)
        return !event.cancelled
    }

    private fun applyReleaseEventMutation(event: ItemReleaseEvent) {
        if (!isDisplayFieldLocked("icon")) {
            backingItem.type = event.icon
        }
        applyLegacyDamage(backingItem, event.data)
        event.itemMeta?.let { backingItem.itemMeta = it }
    }

    private fun readLegacyDamage(itemStack: ItemStack): Int {
        val itemMeta = itemStack.itemMeta
        if (itemMeta != null) {
            val method = itemMeta.javaClass.methods.firstOrNull { method ->
                method.name == "getDamage" && method.parameterCount == 0
            }
            if (method != null) {
                val damage = runCatching { method.invoke(itemMeta) as? Number }.getOrNull()?.toInt()
                if (damage != null) {
                    return damage
                }
            }
        }
        return runCatching { itemStack.durability.toInt() }.getOrDefault(0)
    }

    private fun applyLegacyDamage(itemStack: ItemStack, damage: Int) {
        val value = damage.coerceAtLeast(0)
        val itemMeta = itemStack.itemMeta
        if (itemMeta != null) {
            val method = itemMeta.javaClass.methods.firstOrNull { method ->
                method.name == "setDamage" && method.parameterCount == 1
            }
            if (method != null && runCatching { method.invoke(itemMeta, value) }.isSuccess) {
                itemStack.itemMeta = itemMeta
                return
            }
        }
        runCatching { itemStack.durability = value.toShort() }
    }

    private fun applyI18nDisplay() {
        val player = invocationContext["player"] as? Player ?: return
        val locale = resolveLocale(player) ?: return
        val i18nRoot = runtimeDataBacking["i18n"] as? Map<*, *> ?: return
        val section = resolveI18nSection(i18nRoot, locale) ?: return

        val displayName = parseDisplayName(section["name"])
        if (!displayName.isNullOrBlank()) {
            setDisplayName(displayName)
        }

        val lore = parseLore(section["lore"])
        if (lore.isNotEmpty()) {
            setLore(lore)
        }
    }

    private fun applySelectedDisplay(
        player: Player?,
        source: Any?,
        context: MutableMap<String, Any?>
    ) {
        val api = runCatching { Baikiruto.api() }.getOrNull() ?: return
        val manager = api.getItemManager()
        val item = api.getItem(itemId)
        val preferredDisplayId = (runtimeDataBacking["display-id"] as? String)
            ?: item?.displayId
        val initialDisplay = preferredDisplayId?.let(manager::getDisplay)
        val event = ItemSelectDisplayEvent(
            stream = this,
            player = player,
            source = source,
            context = context,
            displayId = preferredDisplayId,
            display = initialDisplay
        )
        api.getItemEventBus().post(event)
        val selectedDisplay = event.display
            ?: event.displayId?.let(manager::getDisplay)
            ?: initialDisplay
        if (selectedDisplay == null) {
            applyInlineDisplay()
            return
        }
        val buildEvent = ItemReleaseDisplayBuildEvent(
            stream = this,
            player = player,
            source = source,
            context = context,
            displayId = selectedDisplay.id,
            display = selectedDisplay,
            name = resolveDisplayNameVariables(selectedDisplay).toMutableMap(),
            lore = resolveDisplayLoreVariables(selectedDisplay)
                .mapValues { (_, value) -> value.toMutableList() }
                .toMutableMap()
        )
        api.getItemEventBus().post(buildEvent)
        val activeDisplay = buildEvent.display
            ?: buildEvent.displayId?.let(manager::getDisplay)
            ?: selectedDisplay
        putRuntimeData("display-id", activeDisplay.id, ignorePathLock = true)
        val displayProduct = activeDisplay.build(
            name = buildEvent.name,
            lore = buildEvent.lore
        )
        if (!isDisplayFieldLocked("name")) {
            displayProduct.name?.let { displayName ->
                VersionAdapterService.applyDisplayName(backingItem, displayName)
            }
            if (buildEvent.name.isNotEmpty()) {
                putRuntimeData("name", LinkedHashMap(buildEvent.name), ignorePathLock = true)
            }
        }
        if (!isDisplayFieldLocked("lore")) {
            val lore = displayProduct.lore
            if (lore.isNotEmpty()) {
                VersionAdapterService.applyLore(backingItem, lore)
            }
            if (buildEvent.lore.isNotEmpty()) {
                putRuntimeData(
                    "lore",
                    buildEvent.lore.mapValues { (_, value) -> value.toList() },
                    ignorePathLock = true
                )
            }
        }
    }

    private fun applyInlineDisplay() {
        val nameVariables = parseDisplayNameVariables(runtimeDataBacking["name"])
        if (nameVariables.isNotEmpty() && !isDisplayFieldLocked("name")) {
            val displayName = nameVariables["item_name"]
                ?: nameVariables.entries.firstOrNull()?.value
            if (!displayName.isNullOrBlank()) {
                VersionAdapterService.applyDisplayName(backingItem, displayName)
            }
        }

        val loreVariables = parseDisplayLoreVariables(runtimeDataBacking["lore"])
        if (loreVariables.isNotEmpty() && !isDisplayFieldLocked("lore")) {
            val loreLines = loreVariables.entries
                .sortedBy { it.key }
                .flatMap { it.value }
            if (loreLines.isNotEmpty()) {
                VersionAdapterService.applyLore(backingItem, loreLines)
            }
        }
    }

    private fun resolveDisplayNameVariables(display: ItemDisplay): Map<String, String> {
        val runtime = parseDisplayNameVariables(runtimeDataBacking["name"])
        if (runtime.isNotEmpty()) {
            return runtime
        }
        return display.name
    }

    private fun resolveDisplayLoreVariables(display: ItemDisplay): Map<String, List<String>> {
        val runtime = parseDisplayLoreVariables(runtimeDataBacking["lore"])
        if (runtime.isNotEmpty()) {
            return runtime
        }
        return display.lore
    }

    private fun parseDisplayNameVariables(source: Any?): Map<String, String> {
        return when (source) {
            is Map<*, *> -> source.entries.mapNotNull { (key, value) ->
                val normalizedKey = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                normalizedKey to value?.toString().orEmpty()
            }.toMap(linkedMapOf())
            is String -> source.trim().takeIf { it.isNotEmpty() }?.let { mapOf("item_name" to it) } ?: emptyMap()
            else -> emptyMap()
        }
    }

    private fun parseDisplayLoreVariables(source: Any?): Map<String, List<String>> {
        return when (source) {
            is Map<*, *> -> source.entries.mapNotNull { (key, value) ->
                val normalizedKey = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val lines = toStringList(value)
                if (lines.isEmpty()) {
                    return@mapNotNull null
                }
                normalizedKey to lines
            }.toMap(linkedMapOf())
            is String, is Iterable<*> -> toStringList(source).takeIf { it.isNotEmpty() }
                ?.let { mapOf("item_description" to it) }
                ?: emptyMap()
            else -> emptyMap()
        }
    }

    private fun buildInvocationContext(): Map<String, Any?> {
        return LinkedHashMap<String, Any?>().apply {
            putAll(invocationContext)
            putIfAbsent("player", null)
            putIfAbsent("sender", invocationContext["player"])
            putIfAbsent("event", null)
        }
    }

    private fun applyRuntimeDisplay() {
        val itemMeta = backingItem.itemMeta ?: return
        val context = runtimeTemplateContext()
        val displayName = itemMeta.displayName
        if (!isDisplayFieldLocked("name") && !displayName.isNullOrBlank()) {
            itemMeta.setDisplayName(
                LegacyTextColorizer.colorize(
                    renderNameTemplate(displayName, context)
                )
            )
        }
        val lore = itemMeta.lore
        if (!isDisplayFieldLocked("lore") && !lore.isNullOrEmpty()) {
            itemMeta.lore = LegacyTextColorizer.colorize(
                renderLoreTemplates(lore, context)
            ).toMutableList()
        }
        backingItem.itemMeta = itemMeta
    }

    private fun applyPlaceholderDisplay(player: Player?) {
        if (player == null) {
            return
        }
        val itemMeta = backingItem.itemMeta ?: return
        val displayName = itemMeta.displayName
        if (!isDisplayFieldLocked("name") && !displayName.isNullOrBlank()) {
            itemMeta.setDisplayName(
                LegacyTextColorizer.colorize(
                    displayName.replacePlaceholder(player)
                )
            )
        }
        val lore = itemMeta.lore
        if (!isDisplayFieldLocked("lore") && !lore.isNullOrEmpty()) {
            itemMeta.lore = LegacyTextColorizer.colorize(
                lore.replacePlaceholder(player)
            ).toMutableList()
        }
        backingItem.itemMeta = itemMeta
    }

    private fun runtimeTemplateContext(): DisplayTemplateContext {
        val scalar = linkedMapOf<String, String>()
        val list = linkedMapOf<String, MutableList<String>>()
        fun collect(path: String, value: Any?) {
            when (value) {
                is Map<*, *> -> value.forEach { (childKey, childValue) ->
                    val child = childKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                    if (path == "name") {
                        scalar[child] = childValue?.toString().orEmpty()
                    } else if (path == "lore") {
                        val loreValues = toStringList(childValue)
                        if (loreValues.isNotEmpty()) {
                            list[child] = loreValues.toMutableList()
                            scalar[child] = loreValues.first()
                        }
                    }
                    val nextPath = if (path.isBlank()) child else "$path.$child"
                    collect(nextPath, childValue)
                }
                is Iterable<*> -> {
                    val values = toStringList(value)
                    if (values.isNotEmpty()) {
                        list[path] = values.toMutableList()
                        scalar[path] = values.joinToString(",")
                    } else {
                        scalar[path] = ""
                    }
                }
                null -> scalar[path] = "null"
                else -> scalar[path] = value.toString()
            }
        }
        runtimeDataBacking.forEach { (key, value) ->
            if (key == LOCKED_DATA_PATHS_KEY) {
                return@forEach
            }
            if (key == LOCKED_DISPLAY_FIELDS_KEY) {
                return@forEach
            }
            collect(key, value)
        }
        return DisplayTemplateContext(
            scalar = scalar.toMap(),
            list = list.mapValues { it.value.toList() }
        )
    }

    private fun renderNameTemplate(template: String, context: DisplayTemplateContext): String {
        val withAngles = replaceAngleTokens(template, context.scalar, context.list)
        return replaceRuntimeTokens(withAngles, context.scalar)
    }

    private fun renderLoreTemplates(
        templates: List<String>,
        context: DisplayTemplateContext
    ): List<String> {
        val mutableLists = context.list.mapValues { (_, value) -> value.toMutableList() }.toMutableMap()
        val rendered = arrayListOf<String>()
        templates.forEach { template ->
            rendered += expandLoreTemplate(template, context.scalar, mutableLists)
        }
        return rendered
    }

    private fun expandLoreTemplate(
        template: String,
        scalar: Map<String, String>,
        mutableLists: MutableMap<String, MutableList<String>>
    ): List<String> {
        val variables = ANGLE_TOKEN.findAll(template)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val ellipsis = variables
            .filter { it.endsWith("...") && it.length > 3 }
            .distinct()
        if (ellipsis.isEmpty()) {
            return listOf(replaceRuntimeTokens(replaceAngleTokens(template, scalar, mutableLists), scalar))
        }
        val output = arrayListOf<String>()
        while (true) {
            var repeat = false
            var skip = false
            var line = template
            ellipsis.forEach { variable ->
                val key = variable.dropLast(3)
                val values = lookupMutableList(mutableLists, key)
                if (values == null || values.isEmpty()) {
                    skip = true
                    line = replaceFirstLiteral(line, "<$variable>", "")
                } else {
                    line = replaceFirstLiteral(line, "<$variable>", values.removeAt(0))
                    if (values.isNotEmpty()) {
                        repeat = true
                    }
                }
            }
            line = replaceAngleTokens(line, scalar, mutableLists)
            line = replaceRuntimeTokens(line, scalar)
            if (!skip) {
                output += line
            }
            if (!repeat) {
                break
            }
        }
        return output
    }

    private fun replaceAngleTokens(
        input: String,
        scalar: Map<String, String>,
        list: Map<String, List<String>>
    ): String {
        return ANGLE_TOKEN.replace(input) { match ->
            val variable = match.groups[1]?.value?.trim().orEmpty()
            if (variable.isEmpty()) {
                return@replace ""
            }
            val key = if (variable.endsWith("...")) variable.dropLast(3) else variable
            lookupScalar(scalar, key)
                ?: lookupList(list, key)?.firstOrNull()
                ?: ""
        }
    }

    private fun lookupScalar(map: Map<String, String>, key: String): String? {
        return map[key]
            ?: map[key.lowercase()]
            ?: map[key.uppercase()]
    }

    private fun lookupList(map: Map<String, List<String>>, key: String): List<String>? {
        return map[key]
            ?: map[key.lowercase()]
            ?: map[key.uppercase()]
    }

    private fun lookupMutableList(map: MutableMap<String, MutableList<String>>, key: String): MutableList<String>? {
        return map[key]
            ?: map[key.lowercase()]
            ?: map[key.uppercase()]
    }

    private fun replaceFirstLiteral(source: String, target: String, value: String): String {
        val index = source.indexOf(target)
        if (index < 0) {
            return source
        }
        return buildString(source.length - target.length + value.length) {
            append(source, 0, index)
            append(value)
            append(source, index + target.length, source.length)
        }
    }

    private fun toStringList(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> source.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            is Iterable<*> -> source.flatMap { toStringList(it) }
            else -> listOf(source.toString())
        }
    }

    private fun replaceRuntimeTokens(input: String, placeholders: Map<String, String>): String {
        return TOKEN_PATTERN.replace(input) { match ->
            val key = (match.groups[1]?.value ?: match.groups[2]?.value)
                ?.trim()
                .orEmpty()
            if (key.isEmpty()) {
                return@replace match.value
            }
            placeholders[key]
                ?: placeholders[key.lowercase()]
                ?: placeholders[key.uppercase()]
                ?: match.value
        }
    }

    private fun resolveLocale(player: Player): String? {
        val localeMethod = runCatching { player.javaClass.getMethod("getLocale") }.getOrNull() ?: return null
        return runCatching { localeMethod.invoke(player) as? String }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun resolveI18nSection(root: Map<*, *>, locale: String): Map<*, *>? {
        val normalized = locale.lowercase()
            .replace('-', '_')
        val languageOnly = normalized.substringBefore('_')
        return root[locale] as? Map<*, *>
            ?: root[normalized] as? Map<*, *>
            ?: root[languageOnly] as? Map<*, *>
    }

    private fun parseDisplayName(raw: Any?): String? {
        return ComponentConfigParser.parseText(raw) ?: when (raw) {
            is Map<*, *> -> {
                (raw["item_name"] as? String)
                    ?: raw.entries.firstNotNullOfOrNull { (_, value) -> value as? String }
            }
            else -> null
        }
    }

    private fun parseLore(raw: Any?): List<String> {
        val parsed = ComponentConfigParser.parseTextList(raw)
        if (parsed.isNotEmpty()) {
            return parsed
        }
        return when (raw) {
            is String -> raw.split('\n')
            is Iterable<*> -> raw.filterIsInstance<String>().flatMap { it.split('\n') }
            is Map<*, *> -> raw.entries.sortedBy { it.key?.toString().orEmpty() }.flatMap { (_, value) ->
                when (value) {
                    is String -> value.split('\n')
                    is Iterable<*> -> value.filterIsInstance<String>().flatMap { it.split('\n') }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    companion object {

        private const val LOCKED_DATA_PATHS_KEY = "__locked_data_paths__"
        private const val LOCKED_DISPLAY_FIELDS_KEY = "__locked_display_fields__"
        private val TOKEN_PATTERN = Regex("\\{([^{}]+)}|%([^%]+)%")
        private val ANGLE_TOKEN = Regex("<([^<>]+)>")
    }

    private data class DisplayTemplateContext(
        val scalar: Map<String, String>,
        val list: Map<String, List<String>>
    )

    private fun ensureUnlocked(action: String) {
        check(!locked) { "ItemStream[$itemId] is locked, cannot invoke $action." }
    }

    private fun copyRuntimeDataFrom(source: Map<String, Any?>) {
        source.forEach { (key, value) ->
            putRuntimeData(key, value, ignorePathLock = true)
        }
    }

    private fun putRuntimeData(key: String, value: Any?, ignorePathLock: Boolean = false): Boolean {
        val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return false
        if (normalizedKey == LOCKED_DATA_PATHS_KEY) {
            mergeLockedRuntimePaths(value)
            runtimeDataBacking[LOCKED_DATA_PATHS_KEY] = lockedRuntimePaths.toList()
            return true
        }
        if (normalizedKey == LOCKED_DISPLAY_FIELDS_KEY) {
            mergeLockedDisplayFields(value)
            runtimeDataBacking[LOCKED_DISPLAY_FIELDS_KEY] = lockedDisplayFields.toList()
            return true
        }
        if (!ignorePathLock && isRuntimePathLocked(normalizedKey)) {
            return false
        }
        runtimeDataBacking[normalizedKey] = value
        return true
    }

    private fun isRuntimePathLocked(key: String): Boolean {
        return lockedRuntimePaths.any { path ->
            key == path || key.startsWith("$path.")
        }
    }

    private fun mergeLockedRuntimePaths(raw: Any?) {
        when (raw) {
            is String -> raw.split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach(lockedRuntimePaths::add)
            is Iterable<*> -> raw.forEach { entry ->
                val path = entry?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                lockedRuntimePaths += path
            }
        }
    }

    private fun mergeLockedDisplayFields(raw: Any?) {
        when (raw) {
            is String -> raw.split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach(lockedDisplayFields::add)
            is Iterable<*> -> raw.forEach { entry ->
                val field = entry?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                lockedDisplayFields += field
            }
        }
    }

    private fun isDisplayFieldLocked(field: String): Boolean {
        return field in lockedDisplayFields
    }

    private fun enforceDisplayLocks(previous: ItemStack) {
        if (isDisplayFieldLocked("icon") && backingItem.type != lockedIconMaterial) {
            backingItem.type = lockedIconMaterial
        }
        if (!isDisplayFieldLocked("name") && !isDisplayFieldLocked("lore")) {
            return
        }
        val previousMeta = previous.itemMeta ?: return
        val currentMeta = backingItem.itemMeta ?: return
        if (isDisplayFieldLocked("name")) {
            currentMeta.setDisplayName(previousMeta.displayName)
        }
        if (isDisplayFieldLocked("lore")) {
            currentMeta.lore = previousMeta.lore?.toMutableList()
        }
        backingItem.itemMeta = currentMeta
    }
}
