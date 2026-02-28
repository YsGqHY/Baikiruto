package org.tabooproject.baikiruto.module.modern

import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.info

object ModernModuleBootstrap {

    @Awake(LifeCycle.LOAD)
    private fun onLoad() {
        info("[Baikiruto] module-modern loaded.")
    }
}
