package org.tabooproject.baikiruto.impl.hook

import me.arcaniax.hdb.api.DatabaseLoadEvent
import me.arcaniax.hdb.api.HeadDatabaseAPI
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.Ghost
import taboolib.common.platform.event.SubscribeEvent

object HeadDatabaseHook {

    @Volatile
    private var databaseLoaded = false

    @Awake(LifeCycle.ENABLE)
    private fun onEnable() {
        databaseLoaded = isHookAvailable()
    }

    @Ghost
    @SubscribeEvent
    fun onDatabaseLoad(@Suppress("UNUSED_PARAMETER") e: DatabaseLoadEvent) {
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
        val api = runCatching { HeadDatabaseAPI() }.getOrNull() ?: return
        val texture = runCatching { api.getBase64(id) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return
        runtimeData["skull-texture"] = texture
        runtimeData.remove("skull-url")
    }

    fun applyHeadProfile(itemStack: ItemStack, runtimeData: MutableMap<String, Any?>) {
        if (!BaikirutoSettings.headDatabaseHookEnabled) {
            return
        }
        val rawId = runtimeData["skull-head-database"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (!isHookAvailable()) {
            return
        }
        val api = runCatching { HeadDatabaseAPI() }.getOrNull() ?: return
        val id = normalizeHeadDatabaseId(rawId)
        val headItem = runCatching { api.getItemHead(id) }
            .getOrNull()
            ?.clone()
            ?: return

        runCatching { api.getBase64(headItem) }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                runtimeData["skull-texture"] = it
                runtimeData.remove("skull-url")
            }

        val targetMeta = itemStack.itemMeta ?: return
        val sourceMeta = headItem.itemMeta ?: return
        if (!SkullProfileBridge.copy(sourceMeta, targetMeta)) {
            return
        }
        itemStack.itemMeta = targetMeta
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
        return runCatching {
            HeadDatabaseAPI()
            true
        }.getOrDefault(false)
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
