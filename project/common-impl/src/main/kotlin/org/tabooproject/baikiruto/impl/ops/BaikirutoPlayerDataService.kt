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
        initialized = try {
            initializeDatabase()
            setupOnlinePlayers()
            true
        } catch (ex: Throwable) {
            console().sendLang("log-player-data-bootstrap-failed", ex.message.orEmpty())
            false
        }
    }

    @SubscribeEvent
    private fun onJoin(event: PlayerJoinEvent) {
        if (!initialized) {
            return
        }
        try {
            adaptPlayer(event.player).setupDataContainer(BaikirutoSettings.databaseUsernameMode)
        } catch (ex: Throwable) {
            console().sendLang("log-player-data-setup-failed", event.player.name, ex.message.orEmpty())
        }
    }

    @SubscribeEvent
    private fun onQuit(event: PlayerQuitEvent) {
        if (!initialized) {
            return
        }
        try {
            adaptPlayer(event.player).releaseDataContainer()
        } catch (ex: Throwable) {
            console().sendLang("log-player-data-release-failed", event.player.name, ex.message.orEmpty())
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
            try {
                adaptPlayer(player).setupDataContainer(BaikirutoSettings.databaseUsernameMode)
            } catch (ex: Throwable) {
                console().sendLang("log-player-data-setup-failed", player.name, ex.message.orEmpty())
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
