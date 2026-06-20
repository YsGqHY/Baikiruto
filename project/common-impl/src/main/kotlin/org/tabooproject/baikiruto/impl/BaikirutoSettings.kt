package org.tabooproject.baikiruto.impl

import org.bukkit.entity.Player
import org.tabooproject.baikiruto.core.item.DisplayTextPolicy
import org.tabooproject.baikiruto.impl.item.LegacyTextColorizer
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.console
import taboolib.module.configuration.Config
import taboolib.module.configuration.ConfigNode
import taboolib.module.configuration.Configuration
import taboolib.module.lang.sendLang
import java.util.Locale

@ConfigNode(bind = "config.yml")
object BaikirutoSettings {

    @Config(value = "config.yml", autoReload = true)
    lateinit var conf: Configuration
        private set

    @ConfigNode("settings.debug")
    var debug = false

    @ConfigNode("settings.debug-users")
    var debugUsers = listOf<String>()

    /**
     * 当 debug 开关开启时执行 [block]，用于输出调试日志。
     * 使用 inline + lambda 避免 debug 关闭时产生字符串拼接开销。
     */
    inline fun debug(block: () -> Unit) {
        if (debug) block()
    }

    @ConfigNode("settings.mini-message.enabled")
    var miniMessageEnabled = false

    @ConfigNode("update.preserve-enchantments")
    var updatePreserveEnchantments = true

    fun shouldDebugPlayer(player: Player?): Boolean {
        if (!debug || player == null) {
            return false
        }
        val whitelist = debugUsers.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (whitelist.isEmpty()) {
            return true
        }
        val playerName = player.name.trim().lowercase(Locale.ENGLISH)
        val playerUuid = player.uniqueId.toString().trim().lowercase(Locale.ENGLISH)
        return whitelist.any { entry ->
            val normalized = entry.lowercase(Locale.ENGLISH)
            normalized == playerName || normalized == playerUuid
        }
    }

    @ConfigNode("script.preheat.enabled")
    var scriptPreheatEnabled = true

    @ConfigNode("script.preheat.strategy")
    var scriptPreheatStrategy = "ON_ENABLE"

    @ConfigNode("script.preheat.batch-size")
    var scriptPreheatBatchSize = 64

    @ConfigNode("operations.watcher.enabled")
    var watcherEnabled = true

    @ConfigNode("operations.watcher.debounce-ticks")
    var watcherDebounceTicks = 20L

    @ConfigNode("operations.reload-online-update.enabled")
    var reloadOnlineUpdateEnabled = true

    @ConfigNode("operations.async-tick.enabled")
    var asyncTickEnabled = true

    @ConfigNode("operations.async-tick.default-interval")
    var asyncTickDefaultInterval = 100L

    @ConfigNode("operations.performance-log.enabled")
    var performanceLogEnabled = true

    @ConfigNode("operations.performance-log.slow-build-millis")
    var slowBuildMillis = 10L

    @ConfigNode("operations.hook.mythic.enabled")
    var mythicHookEnabled = true

    @ConfigNode("operations.hook.attribute-plus.enabled")
    var attributePlusHookEnabled = true

    @ConfigNode("operations.hook.head-database.enabled")
    var headDatabaseHookEnabled = true

    @ConfigNode("operations.hook.guibind-pro.enabled")
    var guibindProHookEnabled = true

    @ConfigNode("operations.hook.rose-loot.enabled")
    var roseLootHookEnabled = true

    @ConfigNode("database.enabled")
    var databaseEnabled = false

    @ConfigNode("database.host")
    var databaseHost = "localhost"

    @ConfigNode("database.port")
    var databasePort = 3306

    @ConfigNode("database.user")
    var databaseUser = "root"

    @ConfigNode("database.password")
    var databasePassword = "root"

    @ConfigNode("database.database")
    var databaseName = "minecraft"

    @ConfigNode("database.table")
    var databaseTable = ""

    @ConfigNode("database.username-mode")
    var databaseUsernameMode = false

    @ConfigNode("database.sqlite-file")
    var databaseSqliteFile = "data.db"

    @Awake(LifeCycle.ENABLE)
    private fun init() {
        syncDebugSystemProperty()
        syncDisplayTextPolicy()
        reportMiniMessageState()
        conf.onReload {
            syncDebugSystemProperty()
            syncDisplayTextPolicy()
            reportMiniMessageState()
            console().sendLang(
                "log-config-reloaded",
                debug, scriptPreheatEnabled, watcherEnabled, reloadOnlineUpdateEnabled,
                mythicHookEnabled, attributePlusHookEnabled, headDatabaseHookEnabled, guibindProHookEnabled,
                roseLootHookEnabled, databaseEnabled, miniMessageEnabled, LegacyTextColorizer.miniMessageAvailable()
            )
        }
    }

    /**
     * 将 debug 开关同步到系统属性，供 common 模块中无法直接引用 BaikirutoSettings 的组件使用。
     */
    private fun syncDebugSystemProperty() {
        System.setProperty("baikiruto.debug", debug.toString())
    }

    private fun syncDisplayTextPolicy() {
        DisplayTextPolicy.preserveUnknownAngleTags = miniMessageEnabled
    }

    private fun reportMiniMessageState() {
        if (miniMessageEnabled && !LegacyTextColorizer.miniMessageAvailable()) {
            console().sendLang("log-minimessage-unavailable")
        }
    }
}
