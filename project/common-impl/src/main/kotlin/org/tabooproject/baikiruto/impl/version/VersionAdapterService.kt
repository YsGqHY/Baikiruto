package org.tabooproject.baikiruto.impl.version

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.tabooproject.baikiruto.core.version.BaseItemMetaVersionAdapter
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.module.nms.MinecraftVersion

object VersionAdapterService {

    private val runtimeSupport = object : BaseItemMetaVersionAdapter() {

        override val supportsCustomModelData: Boolean
            get() = currentProfile().supportsCustomModelData && !currentProfile().supportsItemModel

        override fun applyItemModel(itemMeta: ItemMeta, modelId: String) {
            if (currentProfile().supportsItemModel) {
                super.applyItemModel(itemMeta, modelId)
            }
        }
    }

    fun currentProfile(): VersionFeatureProfile {
        return detectProfile()
    }

    fun applyDisplayName(itemStack: ItemStack, displayName: String?) {
        runtimeSupport.applyDisplayName(itemStack, displayName)
    }

    fun applyLore(itemStack: ItemStack, lore: List<String>) {
        runtimeSupport.applyLore(itemStack, lore)
    }

    fun readItemData(itemStack: ItemStack): Map<String, Any?> {
        return runtimeSupport.readItemData(itemStack)
    }

    fun applyVersionEffects(itemStack: ItemStack, runtimeData: Map<String, Any?>) {
        val profile = currentProfile()
        val patched = if (profile.supportsItemModel) {
            runtimeData
        } else {
            runtimeData - "item-model"
        }
        runtimeSupport.applyVersionEffects(itemStack, patched)
    }

    @Awake(LifeCycle.ACTIVE)
    private fun onActive() {
        val profile = currentProfile()
        info(
            "[Baikiruto] Version features: profile=${profile.profileId}, " +
                "running=${MinecraftVersion.runningVersion}, versionId=${MinecraftVersion.versionId}, " +
                "legacyNbt=${profile.legacyNbtStorage}, dataComponent=${profile.dataComponentStorage}, " +
                "customModelData=${profile.supportsCustomModelData}, itemModel=${profile.supportsItemModel}"
        )
    }

    private fun detectProfile(): VersionFeatureProfile {
        val versionId = MinecraftVersion.versionId
        val in112Range = MinecraftVersion.isIn(MinecraftVersion.V1_12, MinecraftVersion.V1_12) &&
            versionId in 11200..11299
        val in120Range = MinecraftVersion.isIn(MinecraftVersion.V1_20, MinecraftVersion.V1_20) &&
            versionId in 12000..12099
        val in121Range = MinecraftVersion.isIn(MinecraftVersion.V1_21, MinecraftVersion.V1_21) &&
            versionId in 12100..12199

        val supportsCustomModelData = when {
            versionId in 11400..11999 -> true
            in120Range && versionId <= 12006 -> true
            else -> false
        }
        val supportsItemModel = in121Range
        val dataComponentStorage = (in120Range && versionId >= 12005) || in121Range
        val legacyNbtStorage = in112Range || (versionId in 11300..12004)

        val profileId = when {
            in121Range -> "v1_21.x"
            in120Range && versionId >= 12005 -> "v1_20_5+"
            in120Range -> "v1_20_0-1_20_4"
            versionId in 11400..11999 -> "v1_14-1_19"
            versionId in 11300..11399 -> "v1_13"
            in112Range -> "v1_12"
            else -> "unknown"
        }

        return VersionFeatureProfile(
            profileId = profileId,
            legacyNbtStorage = legacyNbtStorage,
            dataComponentStorage = dataComponentStorage,
            supportsCustomModelData = supportsCustomModelData,
            supportsItemModel = supportsItemModel
        )
    }
}

data class VersionFeatureProfile(
    val profileId: String,
    val legacyNbtStorage: Boolean,
    val dataComponentStorage: Boolean,
    val supportsCustomModelData: Boolean,
    val supportsItemModel: Boolean
)
