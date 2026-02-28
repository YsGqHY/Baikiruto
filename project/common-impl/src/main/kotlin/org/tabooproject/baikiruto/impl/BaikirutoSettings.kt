package org.tabooproject.baikiruto.impl

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.module.configuration.Config
import taboolib.module.configuration.ConfigNode
import taboolib.module.configuration.Configuration

@ConfigNode(bind = "config.yml")
object BaikirutoSettings {

    @Config(value = "config.yml", autoReload = true)
    lateinit var conf: Configuration
        private set

    @ConfigNode("settings.debug")
    var debug = false

    @ConfigNode("settings.debug-users")
    var debugUsers = listOf<String>()

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
        conf.onReload {
            info(
                "[Baikiruto] Config reloaded: debug=$debug, preheat=$scriptPreheatEnabled, " +
                    "watcherEnabled=$watcherEnabled, reloadOnlineUpdate=$reloadOnlineUpdateEnabled, " +
                    "hookMythic=$mythicHookEnabled, hookAttributePlus=$attributePlusHookEnabled, " +
                    "hookHeadDatabase=$headDatabaseHookEnabled, " +
                    "databaseEnabled=$databaseEnabled"
            )
        }
    }
}
