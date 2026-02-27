package org.tabooproject.baikiruto.impl

import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common.platform.function.warning
import taboolib.common.util.unsafeLazy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Baikiruto
 * org.tabooproject.baikiruto.impl.DefaultBaikirutoBooster
 *
 * @author mical
 * @since 2026/2/26 23:06
 */
object DefaultBaikirutoBooster {

    val api by unsafeLazy { DefaultBaikirutoAPI() }
    private val registered = AtomicBoolean(false)

    fun startup() {
        warning("[Baikiruto] Legacy INIT startup bridge invoked, API registration now happens in LOAD.")
    }

    @Awake(LifeCycle.LOAD)
    private fun onLoad() {
        registerApi("LOAD")
    }

    @Awake(LifeCycle.ENABLE)
    private fun onEnable() {
        releaseResourceFile("config.yml")
        info("[Baikiruto] ENABLE completed, resources and configuration are available.")
    }

    @Awake(LifeCycle.ACTIVE)
    private fun onActive() {
        info(
            "[Baikiruto] ACTIVE completed, debug=${BaikirutoSettings.debug}, " +
                "scriptPreheat=${BaikirutoSettings.scriptPreheatEnabled}"
        )
    }

    private fun registerApi(phase: String) {
        if (registered.compareAndSet(false, true)) {
            Baikiruto.register(api)
            info("[Baikiruto] API registered in lifecycle phase: $phase")
        }
    }
}
