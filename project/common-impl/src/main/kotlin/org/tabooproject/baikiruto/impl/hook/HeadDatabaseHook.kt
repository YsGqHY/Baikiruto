package org.tabooproject.baikiruto.impl.hook

import me.arcaniax.hdb.api.HeadDatabaseAPI
import org.bukkit.Bukkit
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent

object HeadDatabaseHook {

    @Volatile
    private var databaseLoaded = false

    @Awake(LifeCycle.ENABLE)
    private fun onEnable() {
        databaseLoaded = isHookAvailable()
    }

    @SubscribeEvent(bind = "me.arcaniax.hdb.api.DatabaseLoadEvent")
    fun onDatabaseLoad(@Suppress("UNUSED_PARAMETER") event: OptionalEvent) {
        databaseLoaded = true
    }

    fun patchSkullData(runtimeData: MutableMap<String, Any?>) {
        if (!BaikirutoSettings.headDatabaseHookEnabled) {
            return
        }
        val rawId = runtimeData["skull-head-database"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val id = normalizeHeadDatabaseId(rawId)
        if (!isHookAvailable()) {
            return
        }
        val texture = runCatching { HeadDatabaseAPI().getBase64(id) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return
        runtimeData["skull-texture"] = texture
        runtimeData.remove("skull-url")
    }

    fun isHookAvailable(): Boolean {
        if (!isApiPresent()) {
            return false
        }
        return runCatching { Bukkit.getPluginManager().isPluginEnabled("HeadDatabase") }.getOrDefault(false)
    }

    fun isDatabaseLoaded(): Boolean {
        return databaseLoaded && isHookAvailable()
    }

    private fun isApiPresent(): Boolean {
        return runCatching { Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI") }.isSuccess
    }

    private fun normalizeHeadDatabaseId(raw: String): String {
        val value = raw.trim()
        return if (value.startsWith("hdb:", true)) {
            value.substringAfter(':').trim()
        } else {
            value
        }
    }
}
