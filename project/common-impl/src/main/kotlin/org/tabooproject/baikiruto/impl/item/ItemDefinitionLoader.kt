package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.BaikirutoScriptSource
import org.tabooproject.baikiruto.core.item.Item
import org.tabooproject.baikiruto.core.item.ItemDisplay
import org.tabooproject.baikiruto.core.item.ItemGroup
import org.tabooproject.baikiruto.core.item.ItemManager
import org.tabooproject.baikiruto.core.item.ItemModel
import org.tabooproject.baikiruto.core.item.ItemScriptHooks
import org.tabooproject.baikiruto.core.item.ItemScriptTrigger
import org.tabooproject.baikiruto.core.item.Meta
import org.tabooproject.baikiruto.core.item.event.PluginReloadEvent
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import org.tabooproject.baikiruto.impl.item.feature.ItemCooldownFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemDataMapperFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemDropEntityFeature
import org.tabooproject.baikiruto.impl.item.feature.ItemProtectionFeature
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.library.configuration.ConfigurationSection
import taboolib.library.xseries.XMaterial
import taboolib.module.configuration.Configuration
import taboolib.module.lang.sendLang
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

object ItemDefinitionLoader {

    private val defaultItemFiles = listOf(
        "items/example.yml"
    )
    private const val DEFAULT_DISPLAY_FILE = "display/def.yml"
    private const val LEGACY_COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx"
    private const val LOCKED_DATA_PATHS_KEY = "__locked_data_paths__"
    private const val LOCKED_DISPLAY_FIELDS_KEY = "__locked_display_fields__"
    private val loadedItemIds = CopyOnWriteArraySet<String>()
    private data class ParsedFile(val file: File, val conf: Configuration)

    @Awake(LifeCycle.ENABLE)
    private fun init() {
        defaultItemFiles.forEach(::releaseResourceFile)
        releaseResourceFile(DEFAULT_DISPLAY_FILE)
    }

    @Awake(LifeCycle.ACTIVE)
    private fun firstLoad() {
        reloadItems("startup")
    }

    fun reloadItems(source: String): Int {
        val api = Baikiruto.api()
        val manager = api.getItemManager()
        val itemRegistry = manager.getItemRegistry()
        val modelRegistry = manager.getModelRegistry()
        val displayRegistry = manager.getDisplayRegistry()
        val groupRegistry = manager.getGroupRegistry()
        val itemsDir = File(getDataFolder(), "items").apply { mkdirs() }
        val displayDir = File(getDataFolder(), "display").apply { mkdirs() }
        val itemFiles = collectYamlFiles(itemsDir)
        val displayFiles = collectYamlFiles(displayDir)
        val parsedItemFiles = itemFiles.mapNotNull { file ->
            loadConfiguration(file)?.let { ParsedFile(file, it) }
        }
        val parsedDisplayFiles = displayFiles.mapNotNull { file ->
            loadConfiguration(file)?.let { ParsedFile(file, it) }
        }

        loadedItemIds.forEach { ItemScriptPreheatService.invalidateItemScripts(it) }
        loadedItemIds.clear()
        itemRegistry.clear()
        modelRegistry.clear()
        displayRegistry.clear()
        groupRegistry.clear()

        parsedItemFiles.mapNotNull { parseGroup(it, itemsDir) }.forEach { groupRegistry.register(it.id, it) }
        parsedItemFiles.flatMap { parseModels(it.conf) }.forEach { modelRegistry.register(it.id, it) }
        parsedDisplayFiles.flatMap { parseDisplays(it.conf, fromItemFile = false) }.forEach { displayRegistry.register(it.id, it) }
        parsedItemFiles.flatMap { parseDisplays(it.conf, fromItemFile = true) }.forEach { displayRegistry.register(it.id, it) }
        PluginReloadEvent.Display(
            source = source,
            loadedDisplays = displayRegistry.keys().size
        ).call()

        val loadedItems = parsedItemFiles.flatMap { parsed ->
            parseItems(parsed, manager, itemsDir)
        }
        loadedItems.forEach { item ->
            if (itemRegistry.contains(item.id)) {
                console().sendLang("log-item-duplicate-id", item.id)
            }
            itemRegistry.register(item.id, item)
            loadedItemIds += item.id
        }
        PluginReloadEvent.Item(
            source = source,
            loadedItems = loadedItems.size,
            loadedModels = modelRegistry.keys().size,
            loadedGroups = groupRegistry.keys().size
        ).call()
        ItemScriptPreheatService.preheatIfEnabled(loadedItems)

        console().sendLang(
            "log-item-reload-complete",
            source, loadedItems.size, modelRegistry.keys().size,
            displayRegistry.keys().size, groupRegistry.keys().size
        )
        return loadedItems.size
    }

    fun loadedIds(): Set<String> {
        return loadedItemIds.toSet()
    }

    fun loadItemFromFile(
        file: File,
        manager: ItemManager = Baikiruto.api().getItemManager()
    ): List<Item> {
        val sources = collectYamlSources(file)
        if (sources.isEmpty()) {
            return emptyList()
        }
        val root = if (file.isDirectory) file else (file.parentFile ?: file.absoluteFile.parentFile ?: file)
        return sources.mapNotNull { source ->
            loadConfiguration(source)?.let { ParsedFile(source, it) }
        }.flatMap { parsed ->
            parseItems(parsed, manager, root)
        }
    }

    fun loadModelFromFile(file: File): List<ItemModel> {
        return collectYamlSources(file).mapNotNull { source ->
            loadConfiguration(source)
        }.flatMap { conf ->
            parseModels(conf)
        }
    }

    fun loadDisplayFromFile(file: File, fromItemFile: Boolean = false): List<ItemDisplay> {
        return collectYamlSources(file).mapNotNull { source ->
            loadConfiguration(source)
        }.flatMap { conf ->
            parseDisplays(conf, fromItemFile)
        }
    }

    fun loadMetaFromSection(
        root: ConfigurationSection,
        manager: ItemManager = Baikiruto.api().getItemManager()
    ): List<Meta> {
        parseMetas(root, manager).takeIf { it.isNotEmpty() }?.let { return it }
        val legacyMetaSection = root.getConfigurationSection("meta") ?: return emptyList()
        return parseMetaEntries(legacyMetaSection, manager)
    }

    private fun parseMetaEntries(section: ConfigurationSection, manager: ItemManager): List<Meta> {
        return section.getKeys(false).map { key ->
            val metaSection = section.getConfigurationSection(key)
            val source = metaSection?.let(::sectionToMap) ?: anyToMap(section.get(key))
            val scripts = if (metaSection != null) {
                parseHooks(
                    metaSection.getConfigurationSection("scripts"),
                    metaSection.getConfigurationSection("event"),
                    i18nSection = metaSection.getConfigurationSection("i18n")
                )
            } else {
                parseHooksFromMaps(
                    anyToMap(source["scripts"]),
                    anyToMap(source["event"]),
                    i18nSources = listOf(anyToMap(source["i18n"]))
                )
            }

            val factoryType = resolveMetaFactoryType(source)
            if (!factoryType.isNullOrBlank()) {
                val factory = resolveMetaFactory(manager, factoryType)
                if (factory == null) {
                    console().sendLang("log-meta-factory-missing", factoryType, key)
                } else {
                    val created = try {
                        factory.create(key, source, scripts)
                    } catch (ex: Exception) {
                        console().sendLang(
                            "log-meta-factory-failed", key, factory.id, ex.message.orEmpty()
                        )
                        null
                    }
                    if (created != null) {
                        return@map created
                    }
                }
            }

            DefaultMeta(id = key, scripts = scripts)
        }
    }

    private fun collectYamlSources(file: File): List<File> {
        if (!file.exists()) {
            return emptyList()
        }
        if (file.isFile) {
            if (file.extension.equals("yml", true) || file.extension.equals("yaml", true)) {
                return listOf(file)
            }
            return emptyList()
        }
        return collectYamlFiles(file)
    }

    private fun collectYamlFiles(root: File): List<File> {
        if (!root.exists()) {
            return emptyList()
        }
        return root.walkTopDown()
            .filter { file ->
                file.isFile && (file.extension.equals("yml", true) || file.extension.equals("yaml", true))
            }
            .toList()
            .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
    }

    private fun loadConfiguration(file: File): Configuration? {
        return try {
            Configuration.loadFromFile(file)
        } catch (ex: Exception) {
            console().sendLang("log-item-file-load-failed", file.name, ex.message.orEmpty())
            null
        }
    }

    private fun parseModels(conf: ConfigurationSection): List<ItemModel> {
        val models = mutableListOf<ItemModel>()
        conf.getConfigurationSection("models")?.let { modelsSection ->
            modelsSection.getKeys(false).forEach { key ->
                modelsSection.getConfigurationSection(key)?.let { section ->
                    models += ItemModel(id = key, data = sectionToMap(section))
                }
            }
        }
        conf.getKeys(false)
            .filter { key -> key.endsWith("$") }
            .forEach { key ->
                conf.getConfigurationSection(key)?.let { section ->
                    models += ItemModel(id = key.dropLast(1), data = sectionToMap(section))
                }
            }
        return models
    }

    private fun parseDisplays(conf: ConfigurationSection, fromItemFile: Boolean): List<ItemDisplay> {
        val displays = mutableListOf<ItemDisplay>()
        conf.getConfigurationSection("displays")?.let { displaysSection ->
            displaysSection.getKeys(false).forEach { key ->
                displaysSection.getConfigurationSection(key)?.let { section ->
                    parseDisplayDefinition(key, section)?.let(displays::add)
                }
            }
        }
        conf.getConfigurationSection("display")?.let { displaySection ->
            parseDisplayDefinition(displaySection.name, displaySection)?.let(displays::add)
        }
        conf.getKeys(false)
            .filterNot { key ->
                key == "items" ||
                    key == "__group__" ||
                    key == "models" ||
                    key == "displays" ||
                    key == "display"
            }
            .forEach { key ->
                conf.getConfigurationSection(key)?.let { section ->
                    if (fromItemFile && section.contains("display")) {
                        return@let
                    }
                    parseDisplayDefinition(key, section)?.let(displays::add)
                }
            }
        return displays
    }

    private fun parseDisplayDefinition(displayId: String, section: ConfigurationSection): ItemDisplay? {
        val id = displayId.trim().takeIf { it.isNotEmpty() } ?: return null
        return ItemDisplay(
            id = id,
            name = parseNameMap(section),
            lore = parseLoreMap(section),
            data = sectionToMap(section)
        )
    }

    private fun parseNameMap(section: ConfigurationSection): Map<String, String> {
        val nameSection = section.getConfigurationSection("name!!")
            ?: section.getConfigurationSection("name")
        if (nameSection != null) {
            val name = linkedMapOf<String, String>()
            nameSection.getKeys(false).forEach { key ->
                parseDisplayTextValue(nameSection.get(key))?.let { parsed ->
                    name[key] = parsed
                }
            }
            if ("item_name" !in name) {
                parseDisplayTextFromSection(nameSection)?.let { parsed ->
                    name["item_name"] = parsed
                }
            }
            if (name.isNotEmpty()) {
                return name
            }
        }
        val direct = section.get("display-name!!")
            ?: section.get("name!!")
            ?: section.get("display-name")
            ?: section.get("name")
        parseDisplayTextValue(direct)?.let { parsed ->
            return mapOf("item_name" to parsed)
        }
        return emptyMap()
    }

    private fun parseLoreMap(section: ConfigurationSection): Map<String, List<String>> {
        val loreSection = section.getConfigurationSection("lore!!")
            ?: section.getConfigurationSection("lore")
        if (loreSection != null) {
            val autoWrap = loreSection.getInt("~autowrap")
            val lore = linkedMapOf<String, List<String>>()
            loreSection.getKeys(false)
                .filterNot { it == "~autowrap" }
                .forEach { key ->
                val value = loreSection.get(key)
                val lines = parseDisplayLoreValue(value, autoWrap)
                if (lines.isNotEmpty()) {
                    lore[key] = lines
                }
            }
            if ("item_description" !in lore) {
                val combined = parseDisplayLoreValue(sectionToMap(loreSection), autoWrap)
                if (combined.isNotEmpty()) {
                    lore["item_description"] = combined
                }
            }
            if (lore.isNotEmpty()) {
                return lore
            }
        }
        val direct = section.get("lore!!") ?: section.get("lore")
        val lines = parseDisplayLoreValue(direct, 0)
        if (lines.isNotEmpty()) {
            return mapOf("item_description" to lines)
        }
        return emptyMap()
    }

    private fun parseGroup(parsed: ParsedFile, itemsRoot: File): ItemGroup? {
        val relative = parsed.file.relativeTo(itemsRoot).invariantSeparatorsPath
        val pathWithoutExt = relative.substringBeforeLast('.')
        val groupSection = parsed.conf.getConfigurationSection("__group__")
        val id = groupSection?.getString("id")?.takeIf { it.isNotBlank() } ?: pathWithoutExt.lowercase(Locale.ENGLISH)
        val parentId = id.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
        val path = groupSection?.getString("path")?.takeIf { it.isNotBlank() } ?: pathWithoutExt
        val priority = groupSection?.getInt("priority", 0) ?: 0
        val icon = groupSection?.getString("icon!!")
            ?: groupSection?.getString("icon")
            ?: groupSection?.getString("display!!")
            ?: groupSection?.getString("display")
        return ItemGroup(id = id, path = path, parentId = parentId, priority = priority, icon = icon)
    }

    private fun resolveGroupId(file: File, itemsRoot: File): String? {
        val relative = file.relativeTo(itemsRoot).invariantSeparatorsPath.substringBeforeLast('.')
        return relative.takeIf { it.isNotBlank() }?.lowercase(Locale.ENGLISH)
    }

    private fun parseModelRefs(section: ConfigurationSection): List<String> {
        val refs = linkedSetOf<String>()
        section.getString("model")
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEach(refs::add)
        section.getStringList("model")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach(refs::add)
        section.getString("from")
            ?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEach(refs::add)
        section.getStringList("from")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach(refs::add)

        val from = section.getString("event.from")
            ?: section.getConfigurationSection("event")?.getString("from")
        from?.split(',', ';')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.forEach(refs::add)
        return refs.toList()
    }

    private fun parseModelRefs(source: Map<String, Any?>): List<String> {
        val refs = linkedSetOf<String>()
        toStringList(source["model"]).forEach(refs::add)
        toStringList(source["from"]).forEach(refs::add)
        val event = anyToMap(source["event"])
        toStringList(event["from"]).forEach(refs::add)
        return refs.toList()
    }

    private fun resolveModels(manager: ItemManager, sourceRefs: List<String>): Pair<List<String>, List<ItemModel>> {
        val orderedIds = linkedSetOf<String>()
        val orderedModels = arrayListOf<ItemModel>()
        val visiting = hashSetOf<String>()

        fun load(id: String) {
            val normalized = id.trim().takeIf { it.isNotEmpty() } ?: return
            if (normalized in orderedIds) {
                return
            }
            if (!visiting.add(normalized)) {
                console().sendLang("log-model-circular", normalized)
                return
            }
            val model = manager.getModel(normalized)
            if (model == null) {
                console().sendLang("log-model-missing", normalized)
                visiting.remove(normalized)
                return
            }
            parseModelRefs(model.data).forEach(::load)
            visiting.remove(normalized)
            if (orderedIds.add(normalized)) {
                orderedModels += model
            }
        }

        sourceRefs.forEach(::load)
        return orderedIds.toList() to orderedModels
    }

    private fun parseItems(parsed: ParsedFile, manager: ItemManager, itemsRoot: File): List<Item> {
        val conf = parsed.conf
        conf.getConfigurationSection("items")?.let { itemsSection ->
            return itemsSection.getKeys(false).mapNotNull { key ->
                parseItemSection(
                    itemKey = key,
                    section = itemsSection.getConfigurationSection(key),
                    manager = manager,
                    groupId = resolveGroupId(parsed.file, itemsRoot)
                )
            }
        }

        if (isSingleItem(conf)) {
            return listOfNotNull(
                parseItemSection(
                    itemKey = parsed.file.nameWithoutExtension,
                    section = conf,
                    manager = manager,
                    groupId = resolveGroupId(parsed.file, itemsRoot)
                )
            )
        }

        val topLevelItems = conf.getKeys(false)
            .filter { key ->
                !key.endsWith("$") &&
                    key != "__group__" &&
                    key != "models" &&
                    key != "displays" &&
                    key != "display"
            }
            .mapNotNull { key ->
                parseItemSection(
                    itemKey = key,
                    section = conf.getConfigurationSection(key),
                    manager = manager,
                    groupId = resolveGroupId(parsed.file, itemsRoot)
                )
            }
        if (topLevelItems.isNotEmpty()) {
            return topLevelItems
        }

        return emptyList()
    }

    private fun parseItemSection(
        itemKey: String,
        section: ConfigurationSection?,
        manager: ItemManager,
        groupId: String?
    ): Item? {
        if (section == null) {
            return null
        }
        val (modelIds, models) = resolveModels(manager, parseModelRefs(section))
        val displayId = section.getString("display!!")?.takeIf { it.isNotBlank() }
            ?: section.getString("display")?.takeIf { it.isNotBlank() }
            ?: models.firstNotNullOfOrNull { model ->
                stringValue(model.data["display!!"])
                    ?: stringValue(model.data["display"])
            }
        val display = displayId?.let(manager::getDisplay)
        val itemId = section.getString("id")?.takeIf { it.isNotBlank() } ?: itemKey
        val materialName = section.getString("material")
            ?: section.getString("material!!")
            ?: section.getString("icon")
            ?: section.getString("icon!!")
            ?: section.getString("type")
            ?: section.getString("type!!")
            ?: models.firstNotNullOfOrNull { model ->
                stringValue(model.data["material"])
                    ?: stringValue(model.data["material!!"])
                    ?: stringValue(model.data["icon"])
                    ?: stringValue(model.data["icon!!"])
                    ?: stringValue(model.data["type"])
                    ?: stringValue(model.data["type!!"])
            }
            ?: "STONE"
        val material = resolveMaterial(materialName)
        val template = ItemStack(material)
        val modelDefaults = mergeModelRuntimeData(models)
        val itemComponents = parseComponents(section.getConfigurationSection("components"))
        val displayRuntime = display?.let {
            mergeRuntimeData(
                it.data,
                parseDisplayAsRuntimeData(it.data["name"], it.data["lore"])
            )
        } ?: emptyMap()
        val itemDisplayRuntime = parseDisplayAsRuntimeData(
            section.get("display-name!!")
                ?: section.get("display-name")
                ?: section.get("name!!")
                ?: section.get("name"),
            section.get("lore!!") ?: section.get("lore")
        )
        val displayName = parseDisplayName(section)
            ?: display?.resolveDisplayName()
            ?: stringValue((itemComponents["name"] as? Map<*, *>)?.get("item_name"))
            ?: stringValue((modelDefaults["name"] as? Map<*, *>)?.get("item_name"))
        val lore = parseLore(section).ifEmpty {
            display?.resolveLore().orEmpty().ifEmpty {
                parseModelLore(mergeRuntimeData(modelDefaults, itemComponents))
            }
        }
        val itemMeta = template.itemMeta
        if (itemMeta != null) {
            if (!displayName.isNullOrBlank()) {
                itemMeta.setDisplayName(LegacyTextColorizer.colorize(displayName))
            }
            if (lore.isNotEmpty()) {
                itemMeta.lore = LegacyTextColorizer.colorize(lore).toMutableList()
            }
            template.itemMeta = itemMeta
        }
        val metas = parseMetas(section, manager)
        val scripts = parseMergedHooks(section, models)
        val eventData = parseMergedEventData(section, models)
        val defaultRuntimeData = LockedDisplaySignature.withLockedValues(
            mergeRuntimeData(
                modelDefaults,
                displayRuntime,
                itemDisplayRuntime,
                parseDisplayLockMetadata(section, models),
                itemComponents,
                parseData(section.getConfigurationSection("data")),
                parseDataMapper(section.getConfigurationSection("data-mapper")),
                parseEffects(section.getConfigurationSection("effects")),
                parseMetaEffects(section.getConfigurationSection("meta")),
                parseI18n(section.getConfigurationSection("i18n"))
            ),
            template
        )
        val versionHash = resolveVersionHash(
            section = section,
            payload = linkedMapOf(
                "itemId" to itemId,
                "item" to sectionToMap(section),
                "modelIds" to modelIds,
                "models" to models.associate { model -> model.id to model.data },
                "displayId" to displayId,
                "display" to display?.data,
                "template" to template.serialize(),
                "runtimeData" to defaultRuntimeData
            )
        )
        return DefaultItem(
            id = itemId,
            template = template.clone(),
            versionHashSupplier = { versionHash },
            groupId = groupId,
            displayId = displayId,
            modelIds = modelIds,
            metas = metas,
            scripts = scripts,
            eventData = eventData,
            defaultRuntimeData = defaultRuntimeData
        )
    }

    private fun resolveVersionHash(section: ConfigurationSection, payload: Map<String, Any?>): String {
        val explicit = section.getString("version-hash")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (explicit != null) {
            return explicit
        }
        return sha1(canonicalizeForHash(payload))
    }

    private fun canonicalizeForHash(source: Any?): String {
        return when (source) {
            null -> "null"
            is String -> "\"${escapeHashToken(source)}\""
            is Number, is Boolean -> source.toString()
            is ConfigurationSection -> canonicalizeForHash(sectionToMap(source))
            is Map<*, *> -> source.entries
                .mapNotNull { (rawKey, value) ->
                    val key = rawKey?.toString() ?: return@mapNotNull null
                    key to canonicalizeForHash(value)
                }
                .sortedBy { it.first }
                .joinToString(prefix = "{", postfix = "}") { (key, value) ->
                    "\"${escapeHashToken(key)}\":$value"
                }
            is Iterable<*> -> source.joinToString(prefix = "[", postfix = "]") { value ->
                canonicalizeForHash(value)
            }
            is Array<*> -> source.joinToString(prefix = "[", postfix = "]") { value ->
                canonicalizeForHash(value)
            }
            else -> "\"${escapeHashToken(source.toString())}\""
        }
    }

    private fun escapeHashToken(source: String): String {
        return source
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun sha1(source: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private fun parseMetas(section: ConfigurationSection, manager: ItemManager): List<Meta> {
        val metasSection = section.getConfigurationSection("metas")
            ?: section.getConfigurationSection("meta-scripts")
            ?: return emptyList()
        return parseMetaEntries(metasSection, manager)
    }

    private fun resolveMetaFactoryType(source: Map<String, Any?>): String? {
        return stringValue(source["type"])
            ?: stringValue(source["factory"])
            ?: stringValue(source["meta_factory"])
            ?: stringValue(source["meta-factory"])
    }

    private fun resolveMetaFactory(manager: ItemManager, rawType: String): org.tabooproject.baikiruto.core.item.MetaFactory? {
        val source = rawType.trim()
        if (source.isEmpty()) {
            return null
        }
        val normalized = source.lowercase(Locale.ENGLISH)
        val namespaced = normalized.substringAfter(':')
        return manager.getMetaFactory(source)
            ?: manager.getMetaFactory(normalized)
            ?: manager.getMetaFactory(namespaced)
    }

    private fun parseMergedHooks(section: ConfigurationSection, models: List<ItemModel>): ItemScriptHooks {
        val scripts = linkedMapOf<String, BaikirutoScriptSource?>()
        val localizedScripts = linkedMapOf<String, MutableMap<String, BaikirutoScriptSource?>>()
        models.forEach { model ->
            collectScriptEntriesFromMap(model.data["scripts"] as? Map<*, *>, scripts)
            collectScriptEntriesFromMap(model.data["event"] as? Map<*, *>, scripts)
            collectI18nScriptEntriesFromMap(model.data["i18n"] as? Map<*, *>, localizedScripts)
        }
        collectScriptEntriesFromSection(section.getConfigurationSection("scripts"), scripts)
        collectScriptEntriesFromSection(section.getConfigurationSection("event"), scripts)
        collectI18nScriptEntriesFromSection(section.getConfigurationSection("i18n"), localizedScripts)
        return buildScriptHooks(scripts, localizedScripts)
    }

    private fun parseMergedEventData(section: ConfigurationSection, models: List<ItemModel>): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        models.forEach { model ->
            merged.putAll(parseEventData(anyToMap(model.data["event"])))
        }
        merged.putAll(parseEventData(section.getConfigurationSection("event")))
        return merged
    }

    private fun parseEventData(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        val dataSection = section.getConfigurationSection("data") ?: return emptyMap()
        return sectionToMap(dataSection)
    }

    private fun parseEventData(source: Map<String, Any?>): Map<String, Any?> {
        if (source.isEmpty()) {
            return emptyMap()
        }
        return anyToMap(source["data"])
    }

    private fun collectScriptEntriesFromSection(
        section: taboolib.library.configuration.ConfigurationSection?,
        target: MutableMap<String, BaikirutoScriptSource?>
    ) {
        if (section == null) {
            return
        }
        section.getKeys(false).forEach { key ->
            val child = section.getConfigurationSection(key)
            target[key] = parseScriptSource(child ?: section.get(key), key)
        }
    }

    private fun collectScriptEntriesFromMap(source: Map<*, *>?, target: MutableMap<String, BaikirutoScriptSource?>) {
        if (source == null) {
            return
        }
        source.forEach { (key, rawValue) ->
            val name = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            target[name] = parseScriptSource(rawValue, name)
        }
    }

    private fun collectI18nScriptEntriesFromSection(
        i18nSection: taboolib.library.configuration.ConfigurationSection?,
        target: MutableMap<String, MutableMap<String, BaikirutoScriptSource?>>
    ) {
        if (i18nSection == null) {
            return
        }
        i18nSection.getKeys(false).forEach { localeKey ->
            val localeSection = i18nSection.getConfigurationSection(localeKey) ?: return@forEach
            val locale = normalizeLocaleKey(localeKey) ?: return@forEach
            val localeScripts = target.getOrPut(locale) { linkedMapOf() }
            collectScriptEntriesFromSection(localeSection.getConfigurationSection("scripts"), localeScripts)
            collectScriptEntriesFromSection(localeSection.getConfigurationSection("event"), localeScripts)
        }
    }

    private fun collectI18nScriptEntriesFromMap(
        i18nMap: Map<*, *>?,
        target: MutableMap<String, MutableMap<String, BaikirutoScriptSource?>>
    ) {
        if (i18nMap == null) {
            return
        }
        i18nMap.forEach { (localeKey, rawSection) ->
            val locale = normalizeLocaleKey(localeKey?.toString()) ?: return@forEach
            val section = anyToMap(rawSection)
            if (section.isEmpty()) {
                return@forEach
            }
            val localeScripts = target.getOrPut(locale) { linkedMapOf() }
            collectScriptEntriesFromMap(anyToMap(section["scripts"]), localeScripts)
            collectScriptEntriesFromMap(anyToMap(section["event"]), localeScripts)
        }
    }

    private fun parseHooksFromMaps(
        vararg sources: Map<String, Any?>,
        i18nSources: List<Map<String, Any?>> = emptyList()
    ): ItemScriptHooks {
        val scripts = linkedMapOf<String, BaikirutoScriptSource?>()
        val localizedScripts = linkedMapOf<String, MutableMap<String, BaikirutoScriptSource?>>()
        sources.forEach { source ->
            collectScriptEntriesFromMap(source, scripts)
        }
        i18nSources.forEach { source ->
            collectI18nScriptEntriesFromMap(source, localizedScripts)
        }
        return buildScriptHooks(scripts, localizedScripts)
    }

    private fun buildScriptHooks(
        scripts: Map<String, BaikirutoScriptSource?>,
        localizedScripts: Map<String, Map<String, BaikirutoScriptSource?>>
    ): ItemScriptHooks {
        val normalized = linkedMapOf<String, BaikirutoScriptSource?>()
        for ((key, source) in scripts) {
            val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: continue
            normalized[normalizedKey] = source
        }
        val normalizedLocalized = linkedMapOf<String, Map<String, BaikirutoScriptSource?>>()
        for ((locale, entries) in localizedScripts) {
            val normalizedLocale = normalizeLocaleKey(locale) ?: continue
            val localeEntries = linkedMapOf<String, BaikirutoScriptSource?>()
            for ((key, source) in entries) {
                val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: continue
                localeEntries[normalizedKey] = source
            }
            if (localeEntries.isNotEmpty()) {
                normalizedLocalized[normalizedLocale] = localeEntries
            }
        }
        return ItemScriptHooks.fromSources(normalized, normalizedLocalized)
    }

    private fun normalizeLocaleKey(source: String?): String? {
        return source
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace('-', '_')
            ?.lowercase(Locale.ENGLISH)
    }

    private fun mergeModelRuntimeData(models: List<ItemModel>): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        models.forEach { model ->
            val data = model.data
            merged.putAll(parseData(anyToMap(data["data"])))
            merged.putAll(parseDataMapper(anyToMap(data["data-mapper"])))
            merged.putAll(anyToMap(data["effects"]))
            merged.putAll(parseMetaEffects(anyToMap(data["meta"])))
            merged.putAll(parseComponents(anyToMap(data["components"])))
            merged.putAll(parseI18n(anyToMap(data["i18n"])))
            data["display"]?.let {
                if (it is Map<*, *>) {
                    merged["display"] = it
                }
            }
            parseDisplayAsRuntimeData(
                data["name!!"] ?: data["name"],
                data["lore!!"] ?: data["lore"]
            ).forEach { (key, value) ->
                merged[key] = value
            }
        }
        return merged
    }

    private fun parseDisplayAsRuntimeData(name: Any?, lore: Any?): Map<String, Any?> {
        val data = linkedMapOf<String, Any?>()
        val nameMap = linkedMapOf<String, String>()
        anyToMap(name).forEach { (key, value) ->
            parseDisplayTextValue(value)?.let { parsed ->
                nameMap[key] = parsed
            }
        }
        if ("item_name" !in nameMap) {
            parseDisplayTextValue(name)?.let { parsed ->
                nameMap["item_name"] = parsed
            }
        }
        if (nameMap.isNotEmpty()) {
            data["name"] = nameMap.toMap()
        }
        val loreMap = linkedMapOf<String, List<String>>()
        anyToMap(lore).forEach { (key, value) ->
            val parsed = parseDisplayLoreValue(value, 0)
            if (parsed.isNotEmpty()) {
                loreMap[key] = parsed
            }
        }
        if ("item_description" !in loreMap) {
            val parsed = parseDisplayLoreValue(lore, 0)
            if (parsed.isNotEmpty()) {
                loreMap["item_description"] = parsed
            }
        }
        if (loreMap.isNotEmpty()) {
            data["lore"] = loreMap.toMap()
        }
        return data
    }

    private fun parseDisplayLockMetadata(
        section: ConfigurationSection,
        models: List<ItemModel>
    ): Map<String, Any?> {
        val fields = linkedSetOf<String>()
        if (containsLockedDisplayKey(section, setOf("display-name!!", "name!!")) ||
            models.any { hasLockedDisplayKey(it.data, setOf("display-name!!", "name!!")) }
        ) {
            fields += "name"
        }
        if (containsLockedDisplayKey(section, setOf("lore!!")) ||
            models.any { hasLockedDisplayKey(it.data, setOf("lore!!")) }
        ) {
            fields += "lore"
        }
        if (containsLockedDisplayKey(section, setOf("material!!", "icon!!", "type!!")) ||
            models.any { hasLockedDisplayKey(it.data, setOf("material!!", "icon!!", "type!!")) }
        ) {
            fields += "icon"
        }
        return if (fields.isEmpty()) {
            emptyMap()
        } else {
            mapOf(LOCKED_DISPLAY_FIELDS_KEY to fields.toList())
        }
    }

    private fun containsLockedDisplayKey(section: ConfigurationSection, keys: Set<String>): Boolean {
        return keys.any { key ->
            section.contains(key) ||
                section.getConfigurationSection(key) != null ||
                !section.getString(key).isNullOrBlank()
        }
    }

    private fun hasLockedDisplayKey(source: Map<String, Any?>, keys: Set<String>): Boolean {
        if (source.isEmpty()) {
            return false
        }
        source.forEach { (key, value) ->
            if (key in keys) {
                return true
            }
            when (value) {
                is Map<*, *> -> if (hasLockedDisplayKey(anyToMap(value), keys)) {
                    return true
                }
                is ConfigurationSection -> if (hasLockedDisplayKey(sectionToMap(value), keys)) {
                    return true
                }
            }
        }
        return false
    }

    private fun parseModelLore(runtimeDefaults: Map<String, Any?>): List<String> {
        val lore = runtimeDefaults["lore"] as? Map<*, *> ?: return emptyList()
        return lore.entries.sortedBy { it.key?.toString().orEmpty() }.flatMap { (_, value) ->
            when (value) {
                is String -> value.split('\n')
                is Iterable<*> -> value.mapNotNull { it?.toString() }.flatMap { it.split('\n') }
                else -> emptyList()
            }
        }
    }

    private fun isSingleItem(section: ConfigurationSection): Boolean {
        return !section.getString("id").isNullOrBlank() ||
            !section.getString("display!!").isNullOrBlank() ||
            !section.getString("display").isNullOrBlank() ||
            !section.getString("material").isNullOrBlank() ||
            !section.getString("material!!").isNullOrBlank() ||
            !section.getString("icon").isNullOrBlank() ||
            !section.getString("icon!!").isNullOrBlank() ||
            !section.getString("type").isNullOrBlank() ||
            !section.getString("type!!").isNullOrBlank() ||
            !section.getString("display-name").isNullOrBlank() ||
            !section.getString("display-name!!").isNullOrBlank() ||
            !section.getString("name").isNullOrBlank() ||
            !section.getString("name!!").isNullOrBlank() ||
            section.getConfigurationSection("name") != null ||
            section.getConfigurationSection("name!!") != null ||
            section.getConfigurationSection("lore") != null ||
            section.getConfigurationSection("lore!!") != null ||
            !section.getString("lore!!").isNullOrBlank() ||
            section.getConfigurationSection("scripts") != null ||
            section.getConfigurationSection("event") != null ||
            section.getConfigurationSection("metas") != null ||
            section.getConfigurationSection("meta-scripts") != null ||
            section.getConfigurationSection("meta") != null ||
            section.getConfigurationSection("effects") != null ||
            section.getConfigurationSection("components") != null ||
            section.getConfigurationSection("data") != null ||
            section.getConfigurationSection("data-mapper") != null ||
            section.getConfigurationSection("i18n") != null
    }

    private fun parseData(section: ConfigurationSection?): Map<String, Any?> {
        return if (section == null) {
            emptyMap()
        } else {
            parseData(sectionToMap(section))
        }
    }

    private fun parseData(source: Map<String, Any?>): Map<String, Any?> {
        if (source.isEmpty()) {
            return emptyMap()
        }
        val normalized = normalizeLockedMap(source).toMutableMap()
        val lockedPaths = collectLockedPaths(source)
        if (lockedPaths.isNotEmpty()) {
            normalized[LOCKED_DATA_PATHS_KEY] = lockedPaths
        }
        return normalized
    }

    private fun parseEffects(section: ConfigurationSection?): Map<String, Any?> {
        return if (section == null) emptyMap() else sectionToMap(section)
    }

    private fun normalizeLockedMap(source: Map<String, Any?>): Map<String, Any?> {
        if (source.isEmpty()) {
            return emptyMap()
        }
        val normalized = linkedMapOf<String, Any?>()
        source.forEach { (key, value) ->
            val targetKey = key.trim()
                .removeSuffix("!!")
                .takeIf { it.isNotEmpty() }
                ?: return@forEach
            normalized[targetKey] = normalizeLockedValue(value)
        }
        return normalized
    }

    private fun normalizeLockedValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> normalizeLockedMap(anyToMap(value))
            is ConfigurationSection -> normalizeLockedMap(sectionToMap(value))
            is Iterable<*> -> value.map { entry -> normalizeLockedValue(entry) }
            else -> value
        }
    }

    private fun collectLockedPaths(source: Map<String, Any?>, prefix: String = ""): List<String> {
        if (source.isEmpty()) {
            return emptyList()
        }
        val paths = linkedSetOf<String>()
        source.forEach { (key, rawValue) ->
            val normalizedKey = key.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            val keyPath = normalizedKey.removeSuffix("!!")
            val fullPath = if (prefix.isBlank()) keyPath else "$prefix.$keyPath"
            if (normalizedKey.endsWith("!!")) {
                paths += fullPath
            }
            when (rawValue) {
                is Map<*, *> -> paths += collectLockedPaths(anyToMap(rawValue), fullPath)
                is ConfigurationSection -> paths += collectLockedPaths(sectionToMap(rawValue), fullPath)
            }
        }
        return paths.toList()
    }

    private fun parseDataMapper(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        val mappings = linkedMapOf<String, Map<String, String>>()
        section.getKeys(false).forEach { key ->
            val raw = section.get(key)
            val script = parseScriptSource(raw, "data-mapper:$key")
            if (script != null) {
                mappings[key] = script.asRuntimeMap()
            }
        }
        return if (mappings.isEmpty()) {
            emptyMap()
        } else {
            mapOf(ItemDataMapperFeature.DATA_MAPPER_KEY to mappings)
        }
    }

    private fun parseDataMapper(section: Map<String, Any?>): Map<String, Any?> {
        if (section.isEmpty()) {
            return emptyMap()
        }
        val mappings = linkedMapOf<String, Map<String, String>>()
        section.forEach { (key, value) ->
            val script = parseScriptSource(value, "data-mapper:$key")
            if (script != null) {
                mappings[key] = script.asRuntimeMap()
            }
        }
        return if (mappings.isEmpty()) {
            emptyMap()
        } else {
            mapOf(ItemDataMapperFeature.DATA_MAPPER_KEY to mappings)
        }
    }

    private fun parseComponents(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        return parseComponents(sectionToMap(section))
    }

    private fun parseComponents(section: Map<String, Any?>): Map<String, Any?> {
        if (section.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        val components = linkedMapOf<String, Any?>()
        section.forEach { (rawComponentId, rawValue) ->
            val componentKey = ComponentConfigParser.normalizeComponentKey(rawComponentId)
            val normalizedComponentValue = normalizeComponentInput(rawValue)
            when (componentKey) {
                "item_model" -> {
                    stringValue(rawValue)?.let { effects["item-model"] = it }
                }
                "custom_model_data" -> {
                    parseCustomModelData(rawValue)?.let { effects["custom-model-data"] = it }
                }
                "custom_name", "name" -> {
                    // 高版本组件字段只保留到 components，避免回填到 legacy meta.name 并覆盖原始显示管线
                }
                "lore" -> {
                    // 高版本组件字段只保留到 components，避免回填到 legacy meta.lore 并覆盖原始显示管线
                }
                "enchantments", "enchantment" -> {
                    parseComponentEnchantments(rawValue).forEach { (key, value) ->
                        effects[key] = value
                    }
                }
                "attribute_modifiers" -> {
                    parseComponentAttributes(rawValue).takeIf { it.isNotEmpty() }?.let {
                        effects["attributes"] = it
                    }
                }
                "unbreakable" -> {
                    val value = when (rawValue) {
                        is Map<*, *> -> true
                        else -> asBoolean(rawValue)
                    }
                    if (value != null) {
                        effects["unbreakable"] = value
                    }
                }
                "damage" -> {
                    numberValue(rawValue)?.toInt()?.let { effects["damage"] = it }
                }
                "max_damage" -> {
                    numberValue(rawValue)?.toInt()?.let { effects["durability"] = it }
                }
                "can_break" -> {
                    val blocks = parseComponentBlocks(rawValue)
                    if (blocks.isNotEmpty()) {
                        effects["can-destroy"] = blocks
                    }
                }
                "can_place_on" -> {
                    val blocks = parseComponentBlocks(rawValue)
                    if (blocks.isNotEmpty()) {
                        effects["can-place-on"] = blocks
                    }
                }
                "tooltip_style" -> {
                    stringValue(rawValue)?.let { effects["tooltip-style"] = it }
                }
                "rarity" -> {
                    stringValue(rawValue)?.let { effects["rarity"] = it }
                }
                "glider" -> {
                    asBoolean(rawValue)?.let { effects["glider"] = it }
                }
                "use_cooldown" -> {
                    val cooldown = anyToMap(rawValue)
                    val seconds = numberValue(cooldown["seconds"])?.toDouble()
                    if (seconds != null && seconds > 0.0) {
                        effects["use-cooldown-seconds"] = seconds
                        effects.putIfAbsent("cooldown", (seconds * 20.0).toLong())
                    }
                    stringValue(cooldown["cooldown_group"])?.let {
                        effects["use-cooldown-group"] = it
                    }
                    parseCooldownApplyOnCancelledTriggers(cooldown).takeIf { it.isNotEmpty() }?.let {
                        effects[ItemCooldownFeature.KEY_APPLY_ON_CANCELLED_TRIGGERS] = it
                    }
                }
                "use_remainder" -> {
                    parseUseRemainder(rawValue).forEach { (key, value) ->
                        effects[key] = value
                    }
                }
                "equippable" -> {
                    parseEquippable(rawValue).forEach { (key, value) ->
                        effects[key] = value
                    }
                }
                "damage_resistant" -> {
                    parseDamageResistant(rawValue).forEach { (key, value) ->
                        effects[key] = value
                    }
                }
                "death_protection" -> {
                    parseDeathProtection(rawValue).forEach { (key, value) ->
                        effects[key] = value
                    }
                }
                "potion_contents" -> {
                    parsePotionContents(rawValue).forEach { (key, value) ->
                        effects[key] = value
                    }
                }
                "custom_data" -> {
                    val customData = anyToMap(rawValue)
                    if (customData.isNotEmpty()) {
                        customData.forEach { (key, value) ->
                            val normalizedKey = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                            effects[normalizedKey] = value
                        }
                    }
                }
            }
            val canonicalKey = when (componentKey) {
                "name" -> "custom_name"
                "enchantment" -> "enchantments"
                else -> componentKey
            }
            if (normalizedComponentValue != null) {
                components[canonicalKey] = normalizedComponentValue
            }
        }
        if (components.isNotEmpty()) {
            effects["components"] = components
        }
        return effects
    }

    private fun normalizeComponentInput(source: Any?): Any? {
        return when (source) {
            null -> null
            is ConfigurationSection -> normalizeComponentInput(sectionToMap(source))
            is Map<*, *> -> source.entries.mapNotNull { (rawKey, rawValue) ->
                val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                key to normalizeComponentInput(rawValue)
            }.toMap(linkedMapOf())
            is Iterable<*> -> source.map { value -> normalizeComponentInput(value) }
            else -> source
        }
    }

    private fun parseMetaEffects(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        parseDropMeta(section).forEach { (key, value) ->
            effects[key] = value
        }
        parseProtectionMeta(section).forEach { (key, value) ->
            effects[key] = value
        }
        if (section.getBoolean("shiny")) {
            effects["glow"] = true
        }
        if (section.contains("unbreakable")) {
            effects["unbreakable"] = section.getBoolean("unbreakable")
        }
        if (section.contains("damage")) {
            effects["damage"] = section.getInt("damage")
        }
        if (section.contains("custom-model-data")) {
            effects["custom-model-data"] = section.getInt("custom-model-data")
        }
        if (section.contains("custommodeldata")) {
            effects["custom-model-data"] = section.getInt("custommodeldata")
        }
        section.getString("item-model")?.takeIf { it.isNotBlank() }?.let {
            effects["item-model"] = it
        }

        val itemFlags = section.getStringList("itemflag").ifEmpty {
            section.getStringList("item-flags")
        }
        if (itemFlags.isNotEmpty()) {
            effects["item-flags"] = itemFlags
        }

        val enchantments = section.getConfigurationSection("enchantment")
            ?: section.getConfigurationSection("enchantments")
        if (enchantments != null) {
            effects["enchantments"] = enchantments.getKeys(false).associateWith { key ->
                enchantments.getInt(key)
            }
        }
        val attributes = parseAttributes(section.getConfigurationSection("attribute"))
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseMetaEffects(ConfigurationSection): attribute section=${section.getConfigurationSection("attribute")?.getKeys(false)}, parsed attributes=$attributes")
        }
        if (attributes.isNotEmpty()) {
            effects["attributes"] = attributes
        }
        if (section.contains("attributes-replace-mode") || section.contains("attributes-replace")) {
            effects["attributes-replace-mode"] = section.getBoolean("attributes-replace-mode",
                section.getBoolean("attributes-replace", false))
        }

        parsePotion(section.getConfigurationSection("potion")).forEach { (key, value) ->
            effects[key] = value
        }
        parseSkull(section.getConfigurationSection("skull")).forEach { (key, value) ->
            effects[key] = value
        }
        parseSpawner(section.getConfigurationSection("spawner")).forEach { (key, value) ->
            effects[key] = value
        }
        parseAdventureBlockList(section).forEach { (key, value) ->
            effects[key] = value
        }
        parseComponents(section.getConfigurationSection("components")).forEach { (key, value) ->
            effects[key] = value
        }

        section.getConfigurationSection("native")?.let {
            effects["native"] = sectionToMap(it)
        }

        parseUnique(section).forEach { (key, value) ->
            effects[key] = value
        }
        parseAsyncTick(section).forEach { (key, value) ->
            effects[key] = value
        }

        val durability = section.getConfigurationSection("durability")
        if (durability != null) {
            if (durability.contains("synchronous")) {
                effects["durability-synchronous"] = durability.getBoolean("synchronous")
            }
            durability.getString("remains")?.takeIf { it.isNotBlank() }?.let {
                effects["durability-remains"] = it
            }
            if (durability.contains("bar-length")) {
                effects["durability-bar-length"] = durability.getInt("bar-length")
            }
            val symbols = durability.getStringList("bar-symbol").ifEmpty {
                durability.getStringList("display-symbol")
            }
            if (symbols.size >= 2) {
                effects["durability-bar-symbol"] = symbols
            }
        }

        val cooldown = section.getConfigurationSection("cooldown")
        if (cooldown != null) {
            val ticks = when {
                cooldown.contains("ticks") -> cooldown.getLong("ticks")
                cooldown.contains("time") -> cooldown.getLong("time")
                cooldown.contains("value") -> cooldown.getLong("value")
                else -> 0L
            }
            if (ticks > 0L) {
                effects["cooldown"] = ticks
            }
            if (cooldown.contains("by-player")) {
                effects["cooldown-by-player"] = cooldown.getBoolean("by-player")
            }
            parseCooldownApplyOnCancelledTriggers(sectionToMap(cooldown)).takeIf { it.isNotEmpty() }?.let {
                effects[ItemCooldownFeature.KEY_APPLY_ON_CANCELLED_TRIGGERS] = it
            }
        } else {
            val rawCooldown = section.get("cooldown")
            val ticks = when (rawCooldown) {
                is Number -> rawCooldown.toLong()
                is String -> rawCooldown.trim().toLongOrNull()
                else -> null
            }
            if (ticks != null && ticks > 0L) {
                effects["cooldown"] = ticks
            }
        }
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseMetaEffects(ConfigurationSection): final effects keys=${effects.keys}, has attributes=${"attributes" in effects}")
        }
        return effects
    }

    private fun parseMetaEffects(section: Map<String, Any?>): Map<String, Any?> {
        if (section.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        parseDropMeta(section).forEach { (key, value) ->
            effects[key] = value
        }
        parseProtectionMeta(section).forEach { (key, value) ->
            effects[key] = value
        }
        if (asBoolean(section["shiny"]) == true) {
            effects["glow"] = true
        }
        section["unbreakable"]?.let { value ->
            asBoolean(value)?.let { effects["unbreakable"] = it }
        }
        section["damage"]?.let { value ->
            numberValue(value)?.toInt()?.let { effects["damage"] = it }
        }
        section["custom-model-data"]?.let { value ->
            numberValue(value)?.toInt()?.let { effects["custom-model-data"] = it }
        }
        section["custommodeldata"]?.let { value ->
            numberValue(value)?.toInt()?.let { effects["custom-model-data"] = it }
        }
        stringValue(section["item-model"])?.let {
            effects["item-model"] = it
        }

        val itemFlags = toStringList(section["itemflag"]).ifEmpty {
            toStringList(section["item-flags"])
        }
        if (itemFlags.isNotEmpty()) {
            effects["item-flags"] = itemFlags
        }

        val enchantments = anyToMap(section["enchantment"]).ifEmpty {
            anyToMap(section["enchantments"])
        }
        if (enchantments.isNotEmpty()) {
            effects["enchantments"] = enchantments.mapValues { (_, value) ->
                numberValue(value)?.toInt() ?: 1
            }
        }
        val attributes = parseAttributes(anyToMap(section["attribute"]))
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseMetaEffects(Map): attribute raw=${section["attribute"]}, parsed attributes=$attributes")
        }
        if (attributes.isNotEmpty()) {
            effects["attributes"] = attributes
        }
        val replaceMode = asBoolean(section["attributes-replace-mode"])
            ?: asBoolean(section["attributes-replace"])
        if (replaceMode != null) {
            effects["attributes-replace-mode"] = replaceMode
        }

        parsePotion(anyToMap(section["potion"])).forEach { (key, value) ->
            effects[key] = value
        }
        parseSkull(anyToMap(section["skull"])).forEach { (key, value) ->
            effects[key] = value
        }
        parseSpawner(anyToMap(section["spawner"])).forEach { (key, value) ->
            effects[key] = value
        }
        parseAdventureBlockList(section).forEach { (key, value) ->
            effects[key] = value
        }
        parseComponents(anyToMap(section["components"])).forEach { (key, value) ->
            effects[key] = value
        }

        anyToMap(section["native"]).takeIf { it.isNotEmpty() }?.let {
            effects["native"] = it
        }
        parseUnique(section).forEach { (key, value) ->
            effects[key] = value
        }
        parseAsyncTick(section).forEach { (key, value) ->
            effects[key] = value
        }

        val durability = anyToMap(section["durability"])
        if (durability.isNotEmpty()) {
            durability["synchronous"]?.let { value ->
                asBoolean(value)?.let { effects["durability-synchronous"] = it }
            }
            stringValue(durability["remains"])?.let {
                effects["durability-remains"] = it
            }
            durability["bar-length"]?.let { value ->
                numberValue(value)?.toInt()?.let { effects["durability-bar-length"] = it }
            }
            val symbols = toStringList(durability["bar-symbol"]).ifEmpty {
                toStringList(durability["display-symbol"])
            }
            if (symbols.size >= 2) {
                effects["durability-bar-symbol"] = symbols
            }
        }

        val cooldown = anyToMap(section["cooldown"])
        if (cooldown.isNotEmpty()) {
            val ticks = numberValue(cooldown["ticks"])
                ?: numberValue(cooldown["time"])
                ?: numberValue(cooldown["value"])
            if (ticks != null && ticks.toLong() > 0L) {
                effects["cooldown"] = ticks.toLong()
            }
            cooldown["by-player"]?.let { value ->
                asBoolean(value)?.let { effects["cooldown-by-player"] = it }
            }
            parseCooldownApplyOnCancelledTriggers(cooldown).takeIf { it.isNotEmpty() }?.let {
                effects[ItemCooldownFeature.KEY_APPLY_ON_CANCELLED_TRIGGERS] = it
            }
        } else {
            val ticks = numberValue(section["cooldown"])?.toLong()
            if (ticks != null && ticks > 0L) {
                effects["cooldown"] = ticks
            }
        }
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseMetaEffects(Map): final effects keys=${effects.keys}, has attributes=${"attributes" in effects}")
        }
        return effects
    }

    private fun parseDropMeta(section: ConfigurationSection): Map<String, Any?> {
        return parseDropMeta(sectionToMap(section))
    }

    private fun parseDropMeta(source: Map<String, Any?>): Map<String, Any?> {
        val effects = linkedMapOf<String, Any?>()
        val drop = anyToMap(readAlias(source, "drop"))
        val displayName = stringValue(
            readAlias(drop, "display-name", "display_name", "displayName", "name")
                ?: readAlias(source, "drop-name", "drop_name", "dropName")
        )
        if (!displayName.isNullOrBlank()) {
            effects[ItemDropEntityFeature.KEY_DISPLAY_NAME] = displayName
        }
        val displayVisible = asBoolean(
            readAlias(drop, "display-visible", "display_visible", "displayVisible", "visible")
                ?: readAlias(source, "drop-visible", "drop_visible", "dropVisible")
        )
        if (displayVisible != null) {
            effects[ItemDropEntityFeature.KEY_DISPLAY_VISIBLE] = displayVisible
        }
        return effects
    }

    private fun parseProtectionMeta(section: ConfigurationSection): Map<String, Any?> {
        return parseProtectionMeta(sectionToMap(section))
    }

    private fun parseProtectionMeta(source: Map<String, Any?>): Map<String, Any?> {
        val effects = linkedMapOf<String, Any?>()
        val roots = listOf(
            anyToMap(readAlias(source, "rules")),
            anyToMap(readAlias(source, "protect")),
            anyToMap(readAlias(source, "protection"))
        )
        roots.forEach { root ->
            if (root.isEmpty()) {
                return@forEach
            }
            parseProtectionCrafting(readAlias(root, "crafting")).forEach { (key, value) ->
                effects[key] = value
            }
            parseProtectionContainers(readAlias(root, "containers", "container")).forEach { (key, value) ->
                effects[key] = value
            }
            parseProtectionDestroy(readAlias(root, "destroy", "damage")).forEach { (key, value) ->
                effects[key] = value
            }
        }

        val noCraft = asBoolean(readAlias(source, "no-craft", "no_craft", "noCraft"))
        if (noCraft != null) {
            effects[ItemProtectionFeature.KEY_CRAFTING_ANY] = noCraft
        }
        val noDestroy = asBoolean(readAlias(source, "no-destroy", "no_destroy", "noDestroy"))
        if (noDestroy != null) {
            effects[ItemProtectionFeature.KEY_DESTROY_ENABLED] = noDestroy
            if (noDestroy && ItemProtectionFeature.KEY_DESTROY_CAUSES !in effects) {
                effects[ItemProtectionFeature.KEY_DESTROY_CAUSES] = listOf("all")
            }
        }
        return effects
    }

    private fun parseProtectionCrafting(source: Any?): Map<String, Any?> {
        val direct = asBoolean(source)
        if (direct != null) {
            return mapOf(ItemProtectionFeature.KEY_CRAFTING_ANY to direct)
        }
        val section = anyToMap(source)
        if (section.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        asBoolean(readAlias(section, "vanilla", "vanilla-crafting", "vanilla_crafting", "vanillaCrafting"))?.let {
            effects[ItemProtectionFeature.KEY_CRAFTING_VANILLA] = it
        }
        asBoolean(readAlias(section, "any", "all"))?.let {
            effects[ItemProtectionFeature.KEY_CRAFTING_ANY] = it
        }
        val stations = toStringList(
            readAlias(section, "stations", "station", "workstations", "work-stations", "work_stations", "workStations")
        ).mapNotNull { station -> ItemProtectionFeature.normalizeStation(station) }
            .distinct()
        if (stations.isNotEmpty()) {
            effects[ItemProtectionFeature.KEY_CRAFTING_STATIONS] = stations
        }
        return effects
    }

    private fun parseProtectionContainers(source: Any?): Map<String, Any?> {
        val denySource = when (source) {
            is Map<*, *>, is ConfigurationSection -> readAlias(anyToMap(source), "deny", "denied", "blocks", "block", "blacklist")
            else -> source
        }
        val denied = toStringList(denySource)
            .mapNotNull { container -> ItemProtectionFeature.normalizeContainer(container) }
            .distinct()
        return if (denied.isEmpty()) {
            emptyMap()
        } else {
            mapOf(ItemProtectionFeature.KEY_CONTAINERS_DENY to denied)
        }
    }

    private fun parseProtectionDestroy(source: Any?): Map<String, Any?> {
        val direct = asBoolean(source)
        if (direct != null) {
            return mapOf(ItemProtectionFeature.KEY_DESTROY_ENABLED to direct)
        }
        val section = anyToMap(source)
        if (section.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        asBoolean(readAlias(section, "enabled", "enable"))?.let {
            effects[ItemProtectionFeature.KEY_DESTROY_ENABLED] = it
        }
        val causes = toStringList(readAlias(section, "causes", "cause", "types", "type", "damage-types", "damageTypes"))
            .mapNotNull { cause -> ItemProtectionFeature.normalizeDestroyCauseToken(cause) }
            .distinct()
        if (causes.isNotEmpty()) {
            effects[ItemProtectionFeature.KEY_DESTROY_CAUSES] = causes
        }
        return effects
    }

    private fun parseCooldownApplyOnCancelledTriggers(source: Map<String, Any?>): List<String> {
        return toStringList(
            readAlias(
                source,
                "apply-on-cancelled-triggers",
                "apply_on_cancelled_triggers",
                "applyOnCancelledTriggers",
                "cancelled-triggers",
                "cancelled_triggers"
            )
        ).mapNotNull { ItemScriptTrigger.fromKey(it)?.key }
            .distinct()
    }

    private fun readAlias(source: Map<String, Any?>, vararg aliases: String): Any? {
        aliases.forEach { alias ->
            if (source.containsKey(alias)) {
                return source[alias]
            }
        }
        val normalizedAliases = aliases.map(::normalizeConfigKey).toSet()
        return source.entries.firstOrNull { (key, _) ->
            normalizeConfigKey(key) in normalizedAliases
        }?.value
    }

    private fun normalizeConfigKey(source: String): String {
        return source
            .trim()
            .filterNot { it == '-' || it == '_' }
            .lowercase(Locale.ENGLISH)
    }

    private fun parsePotion(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        if (section.contains("color")) {
            effects["potion-color"] = section.get("color")
        }
        val baseType = section.getString("base-type")
            ?: section.getString("base")
            ?: section.getString("type")
        if (!baseType.isNullOrBlank()) {
            effects["potion-base-type"] = baseType
            if (section.contains("extended")) {
                effects["potion-base-extended"] = section.getBoolean("extended")
            }
            val upgraded = when {
                section.contains("upgraded") -> section.getBoolean("upgraded")
                section.contains("level") -> section.getInt("level", 1) > 1
                else -> null
            }
            if (upgraded != null) {
                effects["potion-base-upgraded"] = upgraded
            }
        }

        val parsedEffects = mutableListOf<Map<String, Any>>()
        val rootEffects = section.getConfigurationSection("effects")
        if (rootEffects != null) {
            rootEffects.getKeys(false).forEach { effectKey ->
                val child = rootEffects.getConfigurationSection(effectKey)
                if (child != null) {
                    parsedEffects += linkedMapOf<String, Any>(
                        "type" to effectKey,
                        "duration" to child.getInt("duration", 200),
                        "amplifier" to child.getInt("amplifier", 0),
                        "ambient" to child.getBoolean("ambient", false),
                        "particles" to child.getBoolean("particles", true),
                        "icon" to child.getBoolean("icon", true)
                    )
                } else {
                    val value = rootEffects.get(effectKey)?.toString()?.trim() ?: return@forEach
                    val split = value.split(',', ':').map { it.trim() }
                    val duration = split.getOrNull(0)?.toIntOrNull() ?: 200
                    val amplifier = split.getOrNull(1)?.toIntOrNull() ?: 0
                    val ambient = split.getOrNull(2)?.equals("true", true) ?: false
                    val particles = split.getOrNull(3)?.equals("false", true)?.not() ?: true
                    val icon = split.getOrNull(4)?.equals("false", true)?.not() ?: true
                    parsedEffects += linkedMapOf<String, Any>(
                        "type" to effectKey,
                        "duration" to duration,
                        "amplifier" to amplifier,
                        "ambient" to ambient,
                        "particles" to particles,
                        "icon" to icon
                    )
                }
            }
        } else {
            section.getKeys(false)
                .filter { it !in setOf("color", "base", "base-type", "type", "extended", "upgraded", "level") }
                .forEach { effectKey ->
                    val child = section.getConfigurationSection(effectKey) ?: return@forEach
                    parsedEffects += linkedMapOf<String, Any>(
                        "type" to effectKey,
                        "duration" to child.getInt("duration", 200),
                        "amplifier" to child.getInt("amplifier", 0),
                        "ambient" to child.getBoolean("ambient", false),
                        "particles" to child.getBoolean("particles", true),
                        "icon" to child.getBoolean("icon", true)
                    )
                }
        }
        if (parsedEffects.isNotEmpty()) {
            effects["potion-effects"] = parsedEffects
        }
        return effects
    }

    private fun parsePotion(section: Map<String, Any?>): Map<String, Any?> {
        if (section.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        section["color"]?.let { effects["potion-color"] = it }
        val baseType = stringValue(section["base-type"])
            ?: stringValue(section["base"])
            ?: stringValue(section["type"])
        if (!baseType.isNullOrBlank()) {
            effects["potion-base-type"] = baseType
            section["extended"]?.let { value ->
                asBoolean(value)?.let { effects["potion-base-extended"] = it }
            }
            val upgraded = when {
                section.containsKey("upgraded") -> asBoolean(section["upgraded"])
                section.containsKey("level") -> numberValue(section["level"])?.toInt()?.let { it > 1 }
                else -> null
            }
            if (upgraded != null) {
                effects["potion-base-upgraded"] = upgraded
            }
        }

        val parsedEffects = mutableListOf<Map<String, Any>>()
        val rootEffects = anyToMap(section["effects"])
        if (rootEffects.isNotEmpty()) {
            rootEffects.forEach { (effectKey, value) ->
                parsePotionEffect(effectKey, value)?.let(parsedEffects::add)
            }
        } else {
            section.forEach { (effectKey, value) ->
                if (effectKey in setOf("color", "base", "base-type", "type", "extended", "upgraded", "level", "effects")) {
                    return@forEach
                }
                parsePotionEffect(effectKey, value)?.let(parsedEffects::add)
            }
        }
        if (parsedEffects.isNotEmpty()) {
            effects["potion-effects"] = parsedEffects
        }
        return effects
    }

    private fun parseSkull(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        section.getString("owner")?.takeIf { it.isNotBlank() }?.let { effects["skull-owner"] = it }
        section.getString("texture")
            ?.takeIf { it.isNotBlank() }
            ?.let { effects["skull-texture"] = it }
        section.getString("base64")
            ?.takeIf { it.isNotBlank() }
            ?.let { effects["skull-texture"] = it }
        section.getString("url")
            ?.takeIf { it.isNotBlank() }
            ?.let { effects["skull-url"] = it }
        section.getString("signature")
            ?.takeIf { it.isNotBlank() }
            ?.let { effects["skull-signature"] = it }
        section.getString("head-database")
            ?.takeIf { it.isNotBlank() }
            ?.let { effects["skull-head-database"] = normalizeHeadDatabaseId(it) }
        section.getString("head_database")
            ?.takeIf { it.isNotBlank() }
            ?.let { effects["skull-head-database"] = normalizeHeadDatabaseId(it) }
        section.getString("hdb")
            ?.takeIf { it.isNotBlank() }
            ?.let { effects["skull-head-database"] = normalizeHeadDatabaseId(it) }
        return effects
    }

    private fun parseSkull(section: Map<String, Any?>): Map<String, Any?> {
        if (section.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        stringValue(section["owner"])?.let { effects["skull-owner"] = it }
        stringValue(section["texture"])?.let { effects["skull-texture"] = it }
        stringValue(section["base64"])?.let { effects["skull-texture"] = it }
        stringValue(section["url"])?.let { effects["skull-url"] = it }
        stringValue(section["signature"])?.let { effects["skull-signature"] = it }
        stringValue(section["head-database"])?.let { effects["skull-head-database"] = normalizeHeadDatabaseId(it) }
        stringValue(section["head_database"])?.let { effects["skull-head-database"] = normalizeHeadDatabaseId(it) }
        stringValue(section["hdb"])?.let { effects["skull-head-database"] = normalizeHeadDatabaseId(it) }
        return effects
    }

    private fun normalizeHeadDatabaseId(raw: String): String {
        val value = raw.trim()
        return if (value.startsWith("hdb:", true)) {
            value.substringAfter(':').trim()
        } else {
            value
        }
    }

    private fun parseSpawner(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        val entity = section.getString("entity")
            ?: section.getString("type")
            ?: section.getString("entity-type")
        if (!entity.isNullOrBlank()) {
            effects["spawner-entity"] = entity
        }
        if (section.contains("delay")) {
            effects["spawner-delay"] = section.getInt("delay")
        }
        if (section.contains("min-delay")) {
            effects["spawner-min-delay"] = section.getInt("min-delay")
        }
        if (section.contains("max-delay")) {
            effects["spawner-max-delay"] = section.getInt("max-delay")
        }
        if (section.contains("spawn-count")) {
            effects["spawner-spawn-count"] = section.getInt("spawn-count")
        }
        if (section.contains("max-nearby-entities")) {
            effects["spawner-max-nearby-entities"] = section.getInt("max-nearby-entities")
        }
        if (section.contains("required-player-range")) {
            effects["spawner-required-player-range"] = section.getInt("required-player-range")
        }
        if (section.contains("spawn-range")) {
            effects["spawner-spawn-range"] = section.getInt("spawn-range")
        }
        return effects
    }

    private fun parseSpawner(section: Map<String, Any?>): Map<String, Any?> {
        if (section.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        val entity = stringValue(section["entity"])
            ?: stringValue(section["type"])
            ?: stringValue(section["entity-type"])
        if (!entity.isNullOrBlank()) {
            effects["spawner-entity"] = entity
        }
        section["delay"]?.let { numberValue(it)?.toInt()?.let { value -> effects["spawner-delay"] = value } }
        section["min-delay"]?.let { numberValue(it)?.toInt()?.let { value -> effects["spawner-min-delay"] = value } }
        section["max-delay"]?.let { numberValue(it)?.toInt()?.let { value -> effects["spawner-max-delay"] = value } }
        section["spawn-count"]?.let { numberValue(it)?.toInt()?.let { value -> effects["spawner-spawn-count"] = value } }
        section["max-nearby-entities"]?.let { numberValue(it)?.toInt()?.let { value -> effects["spawner-max-nearby-entities"] = value } }
        section["required-player-range"]?.let { numberValue(it)?.toInt()?.let { value -> effects["spawner-required-player-range"] = value } }
        section["spawn-range"]?.let { numberValue(it)?.toInt()?.let { value -> effects["spawner-spawn-range"] = value } }
        return effects
    }

    private fun parseAdventureBlockList(section: ConfigurationSection): Map<String, Any?> {
        val canDestroy = readStringList(section, "can-destroy", "can_destroy")
        val canPlaceOn = readStringList(section, "can-place-on", "can_place_on")
        val effects = linkedMapOf<String, Any?>()
        if (canDestroy.isNotEmpty()) {
            effects["can-destroy"] = canDestroy
        }
        if (canPlaceOn.isNotEmpty()) {
            effects["can-place-on"] = canPlaceOn
        }
        return effects
    }

    private fun parseAdventureBlockList(section: Map<String, Any?>): Map<String, Any?> {
        val canDestroy = readStringList(section, "can-destroy", "can_destroy")
        val canPlaceOn = readStringList(section, "can-place-on", "can_place_on")
        val effects = linkedMapOf<String, Any?>()
        if (canDestroy.isNotEmpty()) {
            effects["can-destroy"] = canDestroy
        }
        if (canPlaceOn.isNotEmpty()) {
            effects["can-place-on"] = canPlaceOn
        }
        return effects
    }

    private fun readStringList(section: ConfigurationSection, vararg keys: String): List<String> {
        keys.forEach { key ->
            val list = section.getStringList(key)
            if (list.isNotEmpty()) {
                return list
            }
            val single = section.getString(key)
            if (!single.isNullOrBlank()) {
                return single.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        return emptyList()
    }

    private fun readStringList(section: Map<String, Any?>, vararg keys: String): List<String> {
        keys.forEach { key ->
            val list = toStringList(section[key])
            if (list.isNotEmpty()) {
                return list
            }
        }
        return emptyList()
    }

    private fun parseAttributes(section: ConfigurationSection?): List<Map<String, Any>> {
        if (section == null) {
            BaikirutoSettings.debug {
                console().sendMessage("[Baikiruto/Debug] parseAttributes(ConfigurationSection): section is null, skipping")
            }
            return emptyList()
        }
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseAttributes(ConfigurationSection): keys=${section.getKeys(false)}")
        }
        val values = arrayListOf<Map<String, Any>>()
        section.getKeys(false).forEach { slotKey ->
            val slotSection = section.getConfigurationSection(slotKey)
            if (slotSection == null) {
                BaikirutoSettings.debug {
                    console().sendMessage("[Baikiruto/Debug]   slot=$slotKey -> no sub-section, skipping")
                }
                return@forEach
            }
            val normalizedSlot = normalizeAttributeSlot(slotKey)
            BaikirutoSettings.debug {
                console().sendMessage("[Baikiruto/Debug]   slot=$slotKey -> normalized=$normalizedSlot, attrKeys=${slotSection.getKeys(false)}")
            }
            slotSection.getKeys(false).forEach { attrKey ->
                val raw = slotSection.get(attrKey)
                if (raw == null) {
                    BaikirutoSettings.debug {
                        console().sendMessage("[Baikiruto/Debug]     attr=$attrKey -> raw value is null, skipping")
                    }
                    return@forEach
                }
                val parsed = parseAttributeModifier(raw.toString())
                if (parsed == null) {
                    BaikirutoSettings.debug {
                        console().sendMessage("[Baikiruto/Debug]     attr=$attrKey -> parseAttributeModifier('$raw') returned null, skipping")
                    }
                    return@forEach
                }
                val entry = linkedMapOf(
                    "attribute" to normalizeAttributeName(attrKey),
                    "amount" to parsed.first,
                    "operation" to parsed.second,
                    "slot" to normalizedSlot
                )
                BaikirutoSettings.debug {
                    console().sendMessage("[Baikiruto/Debug]     attr=$attrKey -> $entry")
                }
                values += entry
            }
        }
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseAttributes(ConfigurationSection): result=${values.size} entries: $values")
        }
        return values
    }

    private fun parseAttributes(section: Map<String, Any?>): List<Map<String, Any>> {
        if (section.isEmpty()) {
            BaikirutoSettings.debug {
                console().sendMessage("[Baikiruto/Debug] parseAttributes(Map): section is empty, skipping")
            }
            return emptyList()
        }
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseAttributes(Map): keys=${section.keys}")
        }
        val values = arrayListOf<Map<String, Any>>()
        section.forEach { (slotKey, rawAttributes) ->
            val attributes = anyToMap(rawAttributes)
            if (attributes.isEmpty()) {
                BaikirutoSettings.debug {
                    console().sendMessage("[Baikiruto/Debug]   slot=$slotKey -> attributes map is empty, skipping")
                }
                return@forEach
            }
            val normalizedSlot = normalizeAttributeSlot(slotKey)
            BaikirutoSettings.debug {
                console().sendMessage("[Baikiruto/Debug]   slot=$slotKey -> normalized=$normalizedSlot, attrKeys=${attributes.keys}")
            }
            attributes.forEach { (attrKey, rawValue) ->
                val parsed = parseAttributeModifier(rawValue?.toString().orEmpty())
                if (parsed == null) {
                    BaikirutoSettings.debug {
                        console().sendMessage("[Baikiruto/Debug]     attr=$attrKey -> parseAttributeModifier('$rawValue') returned null, skipping")
                    }
                    return@forEach
                }
                val entry = linkedMapOf(
                    "attribute" to normalizeAttributeName(attrKey),
                    "amount" to parsed.first,
                    "operation" to parsed.second,
                    "slot" to normalizedSlot
                )
                BaikirutoSettings.debug {
                    console().sendMessage("[Baikiruto/Debug]     attr=$attrKey -> $entry")
                }
                values += entry
            }
        }
        BaikirutoSettings.debug {
            console().sendMessage("[Baikiruto/Debug] parseAttributes(Map): result=${values.size} entries: $values")
        }
        return values
    }

    private fun parseUnique(section: ConfigurationSection): Map<String, Any?> {
        val uniqueSection = section.getConfigurationSection("unique")
        if (uniqueSection != null) {
            return linkedMapOf<String, Any?>(
                "unique-enabled" to uniqueSection.getBoolean("enabled", true),
                "unique-bind-player" to uniqueSection.getBoolean("bind-player", false),
                "unique-deny-message" to uniqueSection.getString("deny-message", "&cThis item belongs to another player.")
            )
        }
        if (!section.contains("unique")) {
            return emptyMap()
        }
        val raw = section.get("unique")
        val enabled = when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.trim().equals("true", true) || raw.trim() == "1"
            else -> false
        }
        return mapOf("unique-enabled" to enabled)
    }

    private fun parseUnique(section: Map<String, Any?>): Map<String, Any?> {
        val uniqueSection = anyToMap(section["unique"])
        if (uniqueSection.isNotEmpty()) {
            return linkedMapOf<String, Any?>(
                "unique-enabled" to (asBoolean(uniqueSection["enabled"]) ?: true),
                "unique-bind-player" to (asBoolean(uniqueSection["bind-player"]) ?: false),
                "unique-deny-message" to (
                    stringValue(uniqueSection["deny-message"])
                        ?: "&cThis item belongs to another player."
                    )
            )
        }
        if (!section.containsKey("unique")) {
            return emptyMap()
        }
        val enabled = asBoolean(section["unique"]) ?: false
        return mapOf("unique-enabled" to enabled)
    }

    private fun parseAsyncTick(section: ConfigurationSection): Map<String, Any?> {
        val asyncTickSection = section.getConfigurationSection("async-tick")
        if (asyncTickSection != null) {
            val enabled = asyncTickSection.getBoolean("enabled", true)
            val effects = linkedMapOf<String, Any?>(ItemAsyncTickPolicy.KEY_ENABLED to enabled)
            if (!enabled) {
                return effects
            }
            val interval = when {
                asyncTickSection.contains("interval") -> asyncTickSection.getLong("interval")
                asyncTickSection.contains("ticks") -> asyncTickSection.getLong("ticks")
                asyncTickSection.contains("period") -> asyncTickSection.getLong("period")
                else -> 0L
            }
            if (interval > 0L) {
                effects[ItemAsyncTickPolicy.KEY_INTERVAL] = interval
            }
            parseAsyncTickConditions(asyncTickSection).forEach { (key, value) ->
                effects[key] = value
            }
            asyncTickSection.getConfigurationSection("conditions")?.let { nested ->
                parseAsyncTickConditions(nested).forEach { (key, value) ->
                    effects[key] = value
                }
            }
            return effects
        }
        if (!section.contains("async-tick")) {
            return emptyMap()
        }
        val raw = section.get("async-tick")
        val enabled = asBoolean(raw)
        if (enabled != null) {
            return mapOf(ItemAsyncTickPolicy.KEY_ENABLED to enabled)
        }
        val interval = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
            else -> null
        }
        return if (interval != null && interval > 0L) {
            linkedMapOf(
                ItemAsyncTickPolicy.KEY_ENABLED to true,
                ItemAsyncTickPolicy.KEY_INTERVAL to interval
            )
        } else {
            emptyMap()
        }
    }

    private fun parseAsyncTick(section: Map<String, Any?>): Map<String, Any?> {
        val asyncTickSection = anyToMap(section["async-tick"])
        if (asyncTickSection.isNotEmpty()) {
            val enabled = asBoolean(asyncTickSection["enabled"]) ?: true
            val effects = linkedMapOf<String, Any?>(ItemAsyncTickPolicy.KEY_ENABLED to enabled)
            if (!enabled) {
                return effects
            }
            val interval = numberValue(asyncTickSection["interval"])
                ?: numberValue(asyncTickSection["ticks"])
                ?: numberValue(asyncTickSection["period"])
            if (interval != null && interval.toLong() > 0L) {
                effects[ItemAsyncTickPolicy.KEY_INTERVAL] = interval.toLong()
            }
            parseAsyncTickConditions(asyncTickSection).forEach { (key, value) ->
                effects[key] = value
            }
            parseAsyncTickConditions(anyToMap(asyncTickSection["conditions"])).forEach { (key, value) ->
                effects[key] = value
            }
            return effects
        }
        if (!section.containsKey("async-tick")) {
            return emptyMap()
        }
        val raw = section["async-tick"]
        val enabled = asBoolean(raw)
        if (enabled != null) {
            return mapOf(ItemAsyncTickPolicy.KEY_ENABLED to enabled)
        }
        val interval = numberValue(raw)?.toLong()
            ?: stringValue(raw)?.toLongOrNull()
        return if (interval != null && interval > 0L) {
            linkedMapOf(
                ItemAsyncTickPolicy.KEY_ENABLED to true,
                ItemAsyncTickPolicy.KEY_INTERVAL to interval
            )
        } else {
            emptyMap()
        }
    }

    private data class AsyncTickBooleanCondition(
        val runtimeKey: String,
        val aliases: List<String>
    )

    private data class AsyncTickListCondition(
        val runtimeKey: String,
        val aliases: List<String>,
        val normalizer: (String) -> String?
    )

    private val asyncTickBooleanConditions = listOf(
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_SNEAKING, listOf("sneaking", "sneak")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_SPRINTING, listOf("sprinting", "sprint")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_SWIMMING, listOf("swimming", "swim")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_GLIDING, listOf("gliding", "glide")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_FLYING, listOf("flying", "fly")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_ON_GROUND, listOf("on-ground", "on_ground", "onGround", "ground")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_IN_VEHICLE, listOf("in-vehicle", "in_vehicle", "inVehicle", "vehicle")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_BURNING, listOf("burning", "burn", "on-fire", "on_fire", "fire")),
        AsyncTickBooleanCondition(ItemAsyncTickPolicy.KEY_CONDITION_BLOCKING, listOf("blocking", "block"))
    )

    private val asyncTickListConditions = listOf(
        AsyncTickListCondition(ItemAsyncTickPolicy.KEY_CONDITION_SLOTS, listOf("slot", "slots"), ItemAsyncTickPolicy::normalizeSlot),
        AsyncTickListCondition(ItemAsyncTickPolicy.KEY_CONDITION_WORLDS, listOf("world", "worlds"), ItemAsyncTickPolicy::normalizeWorld),
        AsyncTickListCondition(ItemAsyncTickPolicy.KEY_CONDITION_GAME_MODES, listOf("game-mode", "game-modes", "gamemode", "gamemodes"), ItemAsyncTickPolicy::normalizeGameMode),
        AsyncTickListCondition(ItemAsyncTickPolicy.KEY_CONDITION_PERMISSIONS, listOf("permission", "permissions", "perm", "perms"), ItemAsyncTickPolicy::normalizePermission)
    )

    private fun parseAsyncTickConditions(section: ConfigurationSection): Map<String, Any?> {
        val effects = linkedMapOf<String, Any?>()
        asyncTickBooleanConditions.forEach { condition ->
            val enabled = findAsyncTickConditionValues(section, condition.aliases)
                .asSequence()
                .mapNotNull(::asBoolean)
                .firstOrNull()
            if (enabled != null) {
                effects[condition.runtimeKey] = enabled
            }
        }
        asyncTickListConditions.forEach { condition ->
            val values = linkedSetOf<String>()
            findAsyncTickConditionValues(section, condition.aliases)
                .flatMap { source -> parseAsyncTickStrings(source, condition.normalizer) }
                .forEach(values::add)
            if (values.isNotEmpty()) {
                effects[condition.runtimeKey] = values.toList()
            }
        }
        return effects
    }

    private fun parseAsyncTickConditions(section: Map<String, Any?>): Map<String, Any?> {
        val effects = linkedMapOf<String, Any?>()
        asyncTickBooleanConditions.forEach { condition ->
            val enabled = findAsyncTickConditionValues(section, condition.aliases)
                .asSequence()
                .mapNotNull(::asBoolean)
                .firstOrNull()
            if (enabled != null) {
                effects[condition.runtimeKey] = enabled
            }
        }
        asyncTickListConditions.forEach { condition ->
            val values = linkedSetOf<String>()
            findAsyncTickConditionValues(section, condition.aliases)
                .flatMap { source -> parseAsyncTickStrings(source, condition.normalizer) }
                .forEach(values::add)
            if (values.isNotEmpty()) {
                effects[condition.runtimeKey] = values.toList()
            }
        }
        return effects
    }

    private fun findAsyncTickConditionValues(section: ConfigurationSection, aliases: List<String>): List<Any?> {
        val values = mutableListOf<Any?>()
        val consumed = mutableSetOf<String>()
        aliases.forEach { alias ->
            if (section.contains(alias)) {
                values += section.get(alias)
                consumed += normalizeAsyncTickConditionAlias(alias)
            }
        }
        val normalizedAliases = aliases.mapTo(mutableSetOf(), ::normalizeAsyncTickConditionAlias)
        section.getKeys(false).forEach { key ->
            val normalized = normalizeAsyncTickConditionAlias(key)
            if (normalized in normalizedAliases && normalized !in consumed) {
                values += section.get(key)
                consumed += normalized
            }
        }
        return values
    }

    private fun findAsyncTickConditionValues(section: Map<String, Any?>, aliases: List<String>): List<Any?> {
        val values = mutableListOf<Any?>()
        val consumed = mutableSetOf<String>()
        aliases.forEach { alias ->
            if (section.containsKey(alias)) {
                values += section[alias]
                consumed += normalizeAsyncTickConditionAlias(alias)
            }
        }
        val normalizedAliases = aliases.mapTo(mutableSetOf(), ::normalizeAsyncTickConditionAlias)
        section.forEach { (key, value) ->
            val normalized = normalizeAsyncTickConditionAlias(key)
            if (normalized in normalizedAliases && normalized !in consumed) {
                values += value
                consumed += normalized
            }
        }
        return values
    }

    private fun normalizeAsyncTickConditionAlias(raw: String): String {
        return raw.trim()
            .lowercase(Locale.ENGLISH)
            .replace("-", "")
            .replace("_", "")
    }

    private fun parseAsyncTickStrings(source: Any?, normalizer: (String) -> String?): List<String> {
        val values = linkedSetOf<String>()
        when (source) {
            null -> Unit
            is String -> addAsyncTickStringValues(values, source, normalizer)
            is Iterable<*> -> source.forEach { entry ->
                when (entry) {
                    null -> Unit
                    is String -> addAsyncTickStringValues(values, entry, normalizer)
                    else -> entry.toString().let(normalizer)?.let(values::add)
                }
            }
            else -> source.toString().let(normalizer)?.let(values::add)
        }
        return values.toList()
    }

    private fun addAsyncTickStringValues(values: MutableSet<String>, source: String, normalizer: (String) -> String?) {
        source.split(',', '\n')
            .mapNotNull(normalizer)
            .forEach(values::add)
    }

    private fun normalizeAttributeName(raw: String): String {
        val normalized = raw.trim().uppercase(Locale.ENGLISH).replace('-', '_')
        return if (normalized.startsWith("GENERIC_")) {
            normalized
        } else {
            "GENERIC_$normalized"
        }
    }

    private fun normalizeAttributeSlot(raw: String): String {
        return when (raw.trim().lowercase(Locale.ENGLISH)) {
            "mainhand", "main_hand", "hand" -> "HAND"
            "offhand", "off_hand" -> "OFF_HAND"
            "head", "helmet" -> "HEAD"
            "chest", "chestplate" -> "CHEST"
            "legs", "leggings" -> "LEGS"
            "feet", "boots" -> "FEET"
            else -> "HAND"
        }
    }

    private fun parseAttributeModifier(raw: String): Pair<Double, String>? {
        val source = raw.trim()
        if (source.isEmpty()) {
            return null
        }
        return if (source.endsWith("%")) {
            val percent = source.dropLast(1).trim().toDoubleOrNull() ?: return null
            percent / 100.0 to "MULTIPLY_SCALAR_1"
        } else {
            source.toDoubleOrNull()?.let { it to "ADD_NUMBER" }
        }
    }

    private fun parseI18n(section: ConfigurationSection?): Map<String, Any?> {
        if (section == null) {
            return emptyMap()
        }
        return mapOf("i18n" to sectionToMap(section))
    }

    private fun parseI18n(section: Map<String, Any?>): Map<String, Any?> {
        if (section.isEmpty()) {
            return emptyMap()
        }
        return mapOf("i18n" to section)
    }

    private fun mergeRuntimeData(vararg chunks: Map<String, Any?>): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        val lockedPaths = linkedSetOf<String>()
        val lockedDisplayFields = linkedSetOf<String>()
        chunks.forEach { chunk ->
            chunk.forEach { (key, value) ->
                if (key == LOCKED_DATA_PATHS_KEY) {
                    when (value) {
                        is Iterable<*> -> value.forEach { entry ->
                            val path = entry?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                            lockedPaths += path
                        }
                        is String -> value.split(',', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach(lockedPaths::add)
                    }
                    return@forEach
                }
                if (key == LOCKED_DISPLAY_FIELDS_KEY) {
                    when (value) {
                        is Iterable<*> -> value.forEach { entry ->
                            val field = entry?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
                            lockedDisplayFields += field
                        }
                        is String -> value.split(',', '\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach(lockedDisplayFields::add)
                    }
                    return@forEach
                }
                if (key == "components") {
                    mergeComponentRuntimeData(merged, value)
                    return@forEach
                }
                merged[key] = value
            }
        }
        if (lockedPaths.isNotEmpty()) {
            merged[LOCKED_DATA_PATHS_KEY] = lockedPaths.toList()
        }
        if (lockedDisplayFields.isNotEmpty()) {
            merged[LOCKED_DISPLAY_FIELDS_KEY] = lockedDisplayFields.toList()
        }
        return merged
    }

    private fun mergeComponentRuntimeData(target: MutableMap<String, Any?>, rawValue: Any?) {
        val incoming = normalizeComponentInput(rawValue) as? Map<*, *> ?: return
        if (incoming.isEmpty()) {
            return
        }
        val current = target["components"] as? Map<*, *>
        if (current == null || current.isEmpty()) {
            target["components"] = incoming
            return
        }
        val merged = linkedMapOf<String, Any?>()
        current.forEach { (rawKey, rawComponentValue) ->
            val key = canonicalComponentKey(rawKey?.toString()) ?: return@forEach
            merged[key] = rawComponentValue
        }
        incoming.forEach { (rawKey, rawComponentValue) ->
            val key = canonicalComponentKey(rawKey?.toString()) ?: return@forEach
            val currentValue = merged[key]
            merged[key] = if (key == "custom_data") {
                mergeCustomDataMaps(currentValue, rawComponentValue)
            } else {
                rawComponentValue
            }
        }
        target["components"] = merged
    }

    private fun canonicalComponentKey(source: String?): String? {
        val normalized = source?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return when (ComponentConfigParser.normalizeComponentKey(normalized)) {
            "name" -> "custom_name"
            "enchantment" -> "enchantments"
            else -> ComponentConfigParser.normalizeComponentKey(normalized)
        }
    }

    private fun mergeCustomDataMaps(oldValue: Any?, newValue: Any?): Any? {
        val oldMap = normalizeComponentInput(oldValue) as? Map<*, *>
        val newMap = normalizeComponentInput(newValue) as? Map<*, *>
        if (oldMap == null || oldMap.isEmpty()) {
            return newValue
        }
        if (newMap == null || newMap.isEmpty()) {
            return oldValue
        }
        val merged = linkedMapOf<String, Any?>()
        oldMap.forEach { (rawKey, rawEntryValue) ->
            val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            merged[key] = rawEntryValue
        }
        newMap.forEach { (rawKey, rawEntryValue) ->
            val key = rawKey?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            merged[key] = rawEntryValue
        }
        return merged
    }

    private fun parseDisplayName(section: ConfigurationSection): String? {
        parseDisplayTextValue(section.get("display-name!!"))?.let { return it }

        val lockedNameSection = section.getConfigurationSection("name!!")
        if (lockedNameSection != null) {
            parseDisplayTextFromSection(lockedNameSection)?.let { return it }
        }

        parseDisplayTextValue(section.get("name!!"))?.let { return it }
        parseDisplayTextValue(section.get("display-name"))?.let { return it }
        parseDisplayTextValue(section.get("name"))?.let { return it }

        val nameSection = section.getConfigurationSection("name") ?: return null
        return parseDisplayTextFromSection(nameSection)
    }

    private fun parseLore(section: ConfigurationSection): List<String> {
        val lockedLoreSection = section.getConfigurationSection("lore!!")
        if (lockedLoreSection != null) {
            val parsed = parseLoreSection(lockedLoreSection)
            if (parsed.isNotEmpty()) {
                return parsed
            }
        }

        parseDisplayLoreValue(section.get("lore!!"), 0).takeIf { it.isNotEmpty() }?.let { return it }

        val loreSection = section.getConfigurationSection("lore")
        if (loreSection != null) {
            val parsed = parseLoreSection(loreSection)
            if (parsed.isNotEmpty()) {
                return parsed
            }
        }

        return parseDisplayLoreValue(section.get("lore"), 0)
    }

    private fun parseLoreSection(loreSection: ConfigurationSection): List<String> {
        val autoWrap = loreSection.getInt("~autowrap")
        val lore = mutableListOf<String>()
        loreSection.getKeys(false).forEach { key ->
            if (key == "~autowrap") {
                return@forEach
            }
            val value = loreSection.get(key)
            val lines = parseDisplayLoreValue(value, autoWrap)
            if (lines.isNotEmpty()) {
                lore += lines
            }
        }
        return lore
    }

    private fun parseDisplayTextFromSection(section: ConfigurationSection): String? {
        return parseDisplayTextValue(sectionToMap(section))
            ?: parseDisplayTextValue(section.get("item_name"))
            ?: section.getKeys(false).firstNotNullOfOrNull { key ->
                parseDisplayTextValue(section.get(key))
            }
    }

    private fun parseDisplayTextValue(source: Any?): String? {
        return ComponentConfigParser.parseText(source)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseDisplayLoreValue(source: Any?, autoWrap: Int): List<String> {
        val parsed = ComponentConfigParser.parseTextList(source)
        val normalized = if (parsed.isNotEmpty()) {
            parsed.flatMap { it.split('\n') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            toStringList(source)
                .flatMap { it.split('\n') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        return applyAutoWrap(normalized, autoWrap)
    }

    private fun applyAutoWrap(lines: List<String>, size: Int): List<String> {
        if (size <= 0) {
            return lines
        }
        return lines.flatMap { line -> wrapLine(line, size) }
    }

    private fun wrapLine(line: String, size: Int): List<String> {
        if (line.length <= size) {
            return listOf(line)
        }
        val result = arrayListOf<String>()
        var remaining = line
        while (remaining.length > size) {
            val chunk = remaining.substring(0, size)
            val carryColor = trailingLegacyColor(chunk)
            result += chunk
            remaining = if (carryColor != null) {
                carryColor + remaining.substring(size)
            } else {
                remaining.substring(size)
            }
        }
        result += remaining
        return result
    }

    private fun trailingLegacyColor(chunk: String): String? {
        for (index in chunk.length - 2 downTo 0) {
            val marker = chunk[index]
            if (marker != '&' && marker != '§') {
                continue
            }
            if (chunk[index + 1] in LEGACY_COLOR_CODES) {
                return "${marker}${chunk[index + 1]}"
            }
        }
        return null
    }

    private fun sectionToMap(section: ConfigurationSection): Map<String, Any?> {
        val values = linkedMapOf<String, Any?>()
        section.getKeys(false).forEach { key ->
            val child = section.getConfigurationSection(key)
            values[key] = if (child != null) {
                sectionToMap(child)
            } else {
                section.get(key)
            }
        }
        return values
    }

    private fun anyToMap(source: Any?): Map<String, Any?> {
        return when (source) {
            null -> emptyMap()
            is ConfigurationSection -> sectionToMap(source)
            is Map<*, *> -> source.entries.mapNotNull { (key, value) ->
                val normalized = key?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                normalized to value
            }.toMap(linkedMapOf())
            else -> emptyMap()
        }
    }

    private fun stringValue(source: Any?): String? {
        return when (source) {
            null -> null
            is String -> source.trim().takeIf { it.isNotEmpty() }
            is Number, is Boolean -> source.toString()
            else -> null
        }
    }

    private fun numberValue(source: Any?): Number? {
        return when (source) {
            null -> null
            is Number -> source
            is String -> source.trim().toLongOrNull() ?: source.trim().toDoubleOrNull()
            else -> null
        }
    }

    private fun asBoolean(source: Any?): Boolean? {
        return when (source) {
            null -> null
            is Boolean -> source
            is Number -> source.toInt() != 0
            is String -> when (source.trim().lowercase(Locale.ENGLISH)) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun toStringList(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> source
                .split('\n', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            is Iterable<*> -> source.flatMap { toStringList(it) }
            else -> source.toString().trim().takeIf { it.isNotEmpty() }?.let(::listOf) ?: emptyList()
        }
    }

    private fun parsePotionEffect(effectKey: String, source: Any?): Map<String, Any>? {
        val normalizedKey = effectKey.trim().takeIf { it.isNotEmpty() } ?: return null
        return when (source) {
            is Map<*, *> -> {
                val values = anyToMap(source)
                linkedMapOf(
                    "type" to normalizedKey,
                    "duration" to (numberValue(values["duration"])?.toInt() ?: 200),
                    "amplifier" to (numberValue(values["amplifier"])?.toInt() ?: 0),
                    "ambient" to (asBoolean(values["ambient"]) ?: false),
                    "particles" to (asBoolean(values["particles"]) ?: true),
                    "icon" to (asBoolean(values["icon"]) ?: true)
                )
            }
            is String -> {
                val split = source.split(',', ':').map { it.trim() }
                linkedMapOf(
                    "type" to normalizedKey,
                    "duration" to (split.getOrNull(0)?.toIntOrNull() ?: 200),
                    "amplifier" to (split.getOrNull(1)?.toIntOrNull() ?: 0),
                    "ambient" to (split.getOrNull(2)?.equals("true", true) ?: false),
                    "particles" to (split.getOrNull(3)?.equals("false", true)?.not() ?: true),
                    "icon" to (split.getOrNull(4)?.equals("false", true)?.not() ?: true)
                )
            }
            else -> null
        }
    }

    private fun parseCustomModelData(source: Any?): Int? {
        numberValue(source)?.toInt()?.let { return it }
        val map = anyToMap(source)
        numberValue(map["value"])?.toInt()?.let { return it }
        numberValue(map["int"])?.toInt()?.let { return it }
        numberValue(map["custom-model-data"])?.toInt()?.let { return it }
        val floats = map["floats"]
        if (floats is Iterable<*>) {
            val first = floats.firstOrNull()
            numberValue(first)?.toInt()?.let { return it }
        }
        return null
    }

    private fun parseComponentBlocks(source: Any?): List<String> {
        return when (source) {
            is String -> toStringList(source)
            is Iterable<*> -> source.flatMap { parseComponentBlocks(it) }.distinct()
            is Map<*, *> -> {
                val map = anyToMap(source)
                val blocks = toStringList(map["blocks"])
                if (blocks.isNotEmpty()) {
                    return blocks
                }
                val predicates = map["predicates"]
                if (predicates is Iterable<*>) {
                    return predicates
                        .flatMap { parseComponentBlocks(anyToMap(it)["blocks"]) }
                        .distinct()
                }
                emptyList()
            }
            else -> emptyList()
        }
    }

    private fun parsePotionContents(source: Any?): Map<String, Any?> {
        val map = anyToMap(source)
        if (map.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>()
        map["custom_color"]?.let {
            effects["potion-color"] = it
        }
        stringValue(map["potion"])?.let {
            effects["potion-base-type"] = it
        }
        val customEffects = map["custom_effects"]
        if (customEffects is Iterable<*>) {
            val parsed = customEffects.mapNotNull { raw ->
                val entry = anyToMap(raw)
                val type = stringValue(entry["id"]) ?: stringValue(entry["type"]) ?: return@mapNotNull null
                linkedMapOf<String, Any>(
                    "type" to type,
                    "duration" to (numberValue(entry["duration"])?.toInt() ?: 200),
                    "amplifier" to (numberValue(entry["amplifier"])?.toInt() ?: 0),
                    "ambient" to (asBoolean(entry["ambient"]) ?: false),
                    "particles" to (asBoolean(entry["show_particles"]) ?: asBoolean(entry["particles"]) ?: true),
                    "icon" to (asBoolean(entry["show_icon"]) ?: asBoolean(entry["icon"]) ?: true)
                )
            }
            if (parsed.isNotEmpty()) {
                effects["potion-effects"] = parsed
            }
        }
        return effects
    }

    private fun parseEquippable(source: Any?): Map<String, Any?> {
        val values = anyToMap(source)
        if (values.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>(
            "equippable" to values
        )
        stringValue(values["slot"])?.let {
            effects["equippable-slot"] = normalizeEquippableSlot(it)
        }
        stringValue(values["equip_sound"])?.let {
            effects["equippable-equip-sound"] = it
        }
        stringValue(values["model"])?.let {
            effects["equippable-model"] = it
        }
        return effects
    }

    private fun parseDamageResistant(source: Any?): Map<String, Any?> {
        val effects = linkedMapOf<String, Any?>()
        when (source) {
            null -> return emptyMap()
            is Boolean, is Number, is String -> {
                asBoolean(source)?.let { enabled ->
                    effects["damage-resistant-enabled"] = enabled
                }
            }
            else -> {
                val map = anyToMap(source)
                if (map.isEmpty()) {
                    return emptyMap()
                }
                effects["damage-resistant"] = map
                effects["damage-resistant-enabled"] = asBoolean(map["enabled"]) ?: true
                val types = parseDamageTypeList(map["types"]).ifEmpty {
                    parseDamageTypeList(map["damage_types"])
                }
                if (types.isNotEmpty()) {
                    effects["damage-resistant-types"] = types
                }
            }
        }
        return effects
    }

    private fun parseDeathProtection(source: Any?): Map<String, Any?> {
        val effects = linkedMapOf<String, Any?>()
        when (source) {
            null -> return emptyMap()
            is Boolean, is Number, is String -> {
                effects["death-protection-enabled"] = asBoolean(source) ?: true
            }
            else -> {
                val map = anyToMap(source)
                if (map.isEmpty()) {
                    return emptyMap()
                }
                effects["death-protection"] = map
                effects["death-protection-enabled"] = asBoolean(map["enabled"]) ?: true
                numberValue(map["health"])?.toDouble()?.let {
                    effects["death-protection-health"] = it
                }
                numberValue(map["amount"])?.toDouble()?.let {
                    effects["death-protection-health"] = it
                }
                asBoolean(map["consume"])?.let {
                    effects["death-protection-consume"] = it
                }
                val types = parseDamageTypeList(map["types"]).ifEmpty {
                    parseDamageTypeList(map["damage_types"])
                }
                if (types.isNotEmpty()) {
                    effects["death-protection-types"] = types
                }
            }
        }
        return effects
    }

    private fun parseComponentDisplayName(source: Any?): String? {
        return ComponentConfigParser.parseText(source)
    }

    private fun parseComponentLore(source: Any?): List<String> {
        return ComponentConfigParser.parseTextList(source)
    }

    private fun parseComponentEnchantments(source: Any?): Map<String, Any?> {
        val root = anyToMap(source)
        if (root.isEmpty()) {
            return emptyMap()
        }
        val levels = anyToMap(root["levels"]).ifEmpty {
            root
        }
        val enchantments = levels.mapNotNull { (key, value) ->
            val level = numberValue(value)?.toInt() ?: return@mapNotNull null
            key to level
        }.toMap(linkedMapOf())
        if (enchantments.isEmpty()) {
            return emptyMap()
        }
        val effects = linkedMapOf<String, Any?>(
            "enchantments" to enchantments
        )
        val showTooltip = asBoolean(root["show_in_tooltip"])
        if (showTooltip == false) {
            val flags = toStringList(effects["item-flags"]).toMutableList()
            if ("HIDE_ENCHANTS" !in flags) {
                flags += "HIDE_ENCHANTS"
            }
            effects["item-flags"] = flags
        }
        return effects
    }

    private fun parseComponentAttributes(source: Any?): List<Map<String, Any>> {
        val root = anyToMap(source)
        if (root.isEmpty()) {
            return emptyList()
        }
        val modifiers = root["modifiers"] as? Iterable<*> ?: return emptyList()
        return modifiers.mapNotNull { modifier ->
            val map = anyToMap(modifier)
            val attribute = stringValue(map["type"]) ?: stringValue(map["attribute"]) ?: return@mapNotNull null
            val amount = numberValue(map["amount"])?.toDouble() ?: return@mapNotNull null
            val operation = normalizeAttributeOperation(map["operation"])
            val slot = stringValue(map["slot"])?.let(::normalizeAttributeSlot) ?: "HAND"
            linkedMapOf(
                "attribute" to normalizeAttributeName(attribute.substringAfter(':')),
                "amount" to amount,
                "operation" to operation,
                "slot" to slot
            )
        }
    }

    private fun normalizeAttributeOperation(source: Any?): String {
        return when (stringValue(source)?.lowercase(Locale.ENGLISH)) {
            "add_value", "add_number" -> "ADD_NUMBER"
            "add_multiplied_base", "add_scalar", "multiply_base" -> "ADD_SCALAR"
            "add_multiplied_total", "multiply_scalar_1", "multiply_total" -> "MULTIPLY_SCALAR_1"
            else -> "ADD_NUMBER"
        }
    }

    private fun normalizeEquippableSlot(source: String): String {
        return when (source.trim().lowercase(Locale.ENGLISH).replace('-', '_')) {
            "mainhand", "main_hand", "hand" -> "MAINHAND"
            "offhand", "off_hand", "offhand_item" -> "OFFHAND"
            "head", "helmet" -> "HEAD"
            "chest", "chestplate", "body" -> "CHEST"
            "legs", "leggings" -> "LEGS"
            "feet", "boots" -> "FEET"
            else -> source.trim().uppercase(Locale.ENGLISH)
        }
    }

    private fun parseDamageTypeList(source: Any?): List<String> {
        return when (source) {
            null -> emptyList()
            is String -> listOf(normalizeDamageType(source))
            is Iterable<*> -> source.mapNotNull { value ->
                value?.toString()?.let(::normalizeDamageType)
            }.filter { it.isNotEmpty() }.distinct()
            else -> emptyList()
        }
    }

    private fun normalizeDamageType(source: String): String {
        return source.trim()
            .lowercase(Locale.ENGLISH)
            .substringAfter(':')
            .replace('-', '_')
    }

    private fun parseUseRemainder(source: Any?): Map<String, Any?> {
        if (source == null) {
            return emptyMap()
        }
        return when (source) {
            is String -> {
                val id = source.trim().takeIf { it.isNotEmpty() } ?: return emptyMap()
                mapOf("use-remainder" to id, "use-remainder-amount" to 1)
            }
            else -> {
                val map = anyToMap(source)
                if (map.isEmpty()) {
                    return emptyMap()
                }
                val id = stringValue(map["id"])
                    ?: stringValue(map["item"])
                    ?: stringValue(map["type"])
                    ?: return emptyMap()
                val amount = numberValue(map["count"])
                    ?: numberValue(map["amount"])
                    ?: 1
                mapOf("use-remainder" to id, "use-remainder-amount" to amount.toInt().coerceAtLeast(1))
            }
        }
    }

    private fun resolveMaterial(source: String?): Material {
        val materialName = source?.trim()?.takeIf { it.isNotEmpty() } ?: return Material.STONE
        return Material.matchMaterial(materialName)
            ?: Material.matchMaterial(materialName.uppercase(Locale.ENGLISH))
            ?: try {
                XMaterial.matchXMaterial(materialName).orElse(null)?.parseMaterial()
            } catch (_: Exception) {
                null
            }
            ?: Material.STONE
    }

    private fun parseHooks(
        vararg sections: taboolib.library.configuration.ConfigurationSection?,
        i18nSection: taboolib.library.configuration.ConfigurationSection? = null
    ): ItemScriptHooks {
        val scripts = linkedMapOf<String, BaikirutoScriptSource?>()
        val localizedScripts = linkedMapOf<String, MutableMap<String, BaikirutoScriptSource?>>()
        sections.filterNotNull().forEach { section ->
            section.getKeys(false).forEach { key ->
                val child = section.getConfigurationSection(key)
                scripts[key] = parseScriptSource(child ?: section.get(key), key)
            }
        }
        collectI18nScriptEntriesFromSection(i18nSection, localizedScripts)
        return buildScriptHooks(scripts, localizedScripts)
    }

    private fun parseScriptSource(source: Any?, scriptId: String? = null): BaikirutoScriptSource? {
        val parsed = when (source) {
            null -> null
            is BaikirutoScriptSource -> source
            is String -> BaikirutoScriptSource.of(source)
            is Iterable<*> -> BaikirutoScriptSource.of(source.mapNotNull { it?.toString() }.joinToString("\n"))
            is Map<*, *> -> {
                val type = stringValue(source["type"])
                    ?: stringValue(source["engine"])
                    ?: BaikirutoScriptSource.DEFAULT_TYPE
                val content = parseScriptValue(
                    source["script"]
                        ?: source["source"]
                        ?: source["content"]
                )
                BaikirutoScriptSource.of(content, type)
            }
            is ConfigurationSection -> {
                val type = source.getString("type")
                    ?: source.getString("engine")
                    ?: BaikirutoScriptSource.DEFAULT_TYPE
                val content = parseScriptValue(
                    source.get("script")
                        ?: source.get("source")
                        ?: source.get("content")
                )
                BaikirutoScriptSource.of(content, type)
            }
            else -> BaikirutoScriptSource.of(source.toString())
        } ?: return null
        val normalized = BaikirutoScriptSource.of(parsed.content, parsed.normalizedType()) ?: return null
        val scriptHandler = Baikiruto.apiOrNull()?.getScriptHandler() ?: return normalized
        val knownType = scriptHandler.getScriptType(normalized.normalizedType())
        if (knownType == null) {
            console().sendLang("log-script-type-missing", normalized.normalizedType(), scriptId ?: "unknown")
            return null
        }
        return normalized
    }

    private fun parseScriptValue(source: Any?): String? {
        val value = when (source) {
            null -> null
            is String -> source
            is Iterable<*> -> source.mapNotNull { it?.toString() }.joinToString("\n")
            is Map<*, *> -> parseScriptValue(
                source["script"]
                    ?: source["source"]
                    ?: source["content"]
            )
            is ConfigurationSection -> parseScriptValue(
                source.get("script")
                    ?: source.get("source")
                    ?: source.get("content")
            )
            else -> source.toString()
        }
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
