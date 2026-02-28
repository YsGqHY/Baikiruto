package org.tabooproject.baikiruto.impl.ops

import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.LifeCycle
import taboolib.common.io.newFile
import taboolib.common.platform.Awake
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.adaptPlayer
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.info
import taboolib.common.platform.function.pluginId
import taboolib.common.platform.function.warning
import taboolib.expansion.releaseDataContainer
import taboolib.expansion.setupDataContainer
import taboolib.expansion.setupPlayerDatabase
import java.io.File

object BaikirutoPlayerDataService {

    @Volatile
    private var initialized = false

    fun isInitialized(): Boolean {
        return initialized
    }

    @Awake(LifeCycle.ENABLE)
    private fun onEnable() {
        initialized = runCatching {
            initializeDatabase()
            setupOnlinePlayers()
            true
        }.onFailure {
            warning("[Baikiruto] Player data bootstrap failed: ${it.message}")
        }.getOrDefault(false)
    }

    @SubscribeEvent
    private fun onJoin(event: PlayerJoinEvent) {
        if (!initialized) {
            return
        }
        runCatching {
            adaptPlayer(event.player).setupDataContainer(BaikirutoSettings.databaseUsernameMode)
        }.onFailure {
            warning("[Baikiruto] Failed to setup data container for ${event.player.name}: ${it.message}")
        }
    }

    @SubscribeEvent
    private fun onQuit(event: PlayerQuitEvent) {
        if (!initialized) {
            return
        }
        runCatching {
            adaptPlayer(event.player).releaseDataContainer()
        }
    }

    private fun initializeDatabase() {
        val tableName = resolveTableName()
        if (BaikirutoSettings.databaseEnabled) {
            setupPlayerDatabase(
                host = BaikirutoSettings.databaseHost,
                port = BaikirutoSettings.databasePort,
                user = BaikirutoSettings.databaseUser,
                password = BaikirutoSettings.databasePassword,
                database = BaikirutoSettings.databaseName,
                table = tableName
            )
            info(
                "[Baikiruto] Player data storage initialized: type=MYSQL, " +
                    "host=${BaikirutoSettings.databaseHost}:${BaikirutoSettings.databasePort}, table=$tableName"
            )
            return
        }
        val file = resolveSqliteFile()
        setupPlayerDatabase(file, tableName)
        info("[Baikiruto] Player data storage initialized: type=SQLITE, file=${file.name}, table=$tableName")
    }

    private fun setupOnlinePlayers() {
        Bukkit.getOnlinePlayers().forEach { player ->
            runCatching {
                adaptPlayer(player).setupDataContainer(BaikirutoSettings.databaseUsernameMode)
            }
        }
    }

    private fun resolveTableName(): String {
        return BaikirutoSettings.databaseTable.trim().ifEmpty {
            "${pluginId.lowercase()}_database"
        }
    }

    private fun resolveSqliteFile(): File {
        val fileName = BaikirutoSettings.databaseSqliteFile.trim().ifEmpty { "data.db" }
        return newFile(getDataFolder(), fileName)
    }
}
