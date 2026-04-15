package org.tabooproject.baikiruto.impl.hook

import me.arcaniax.hdb.api.DatabaseLoadEvent
import me.arcaniax.hdb.api.HeadDatabaseAPI
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.ClassAccess
import org.tabooproject.baikiruto.impl.BaikirutoSettings
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.Ghost
import taboolib.common.platform.event.SubscribeEvent

object HeadDatabaseHook {

    private const val HEAD_DATABASE_PLUGIN = "HeadDatabase"
    private const val HEAD_DATABASE_API_CLASS = "me.arcaniax.hdb.api.HeadDatabaseAPI"

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
        val api = resolveApi() ?: return
        val id = normalizeHeadDatabaseId(rawId)
        val texture = try {
            api.getBase64(id)
        } catch (_: Throwable) {
            null
        }
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
        val api = resolveApi() ?: return
        val id = normalizeHeadDatabaseId(rawId)
        val headItem = try {
            api.getItemHead(id)
        } catch (_: Throwable) {
            null
        }
            ?.clone()
            ?: return

        val texture = try {
            api.getBase64(headItem)
        } catch (_: Throwable) {
            null
        }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (texture != null) {
            runtimeData["skull-texture"] = texture
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
        return isPluginEnabled() && isApiPresent()
    }

    fun isDatabaseLoaded(): Boolean {
        return databaseLoaded && isHookAvailable()
    }

    private fun resolveApi(): HeadDatabaseAPI? {
        if (!isHookAvailable()) {
            return null
        }
        return try {
            HeadDatabaseAPI()
        } catch (_: Exception) {
            null
        }
    }

    private fun isPluginEnabled(): Boolean {
        val plugin = Bukkit.getPluginManager().getPlugin(HEAD_DATABASE_PLUGIN) ?: return false
        return plugin.isEnabled
    }

    private fun isApiPresent(): Boolean {
        return ClassAccess.isAvailable(HEAD_DATABASE_API_CLASS, javaClass.classLoader)
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
