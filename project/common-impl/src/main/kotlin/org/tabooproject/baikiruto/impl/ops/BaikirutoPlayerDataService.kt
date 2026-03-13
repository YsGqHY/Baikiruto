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
import taboolib.common.platform.function.console
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.pluginId
import taboolib.expansion.releaseDataContainer
import taboolib.expansion.setupDataContainer
import taboolib.expansion.setupPlayerDatabase
import taboolib.module.lang.sendLang
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
            console().sendLang("log-player-data-bootstrap-failed", it.message.orEmpty())
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
            console().sendLang("log-player-data-setup-failed", event.player.name, it.message.orEmpty())
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
            console().sendLang(
                "log-player-data-mysql",
                BaikirutoSettings.databaseHost, BaikirutoSettings.databasePort, tableName
            )
            return
        }
        val file = resolveSqliteFile()
        setupPlayerDatabase(file, tableName)
        console().sendLang("log-player-data-sqlite", file.name, tableName)
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
