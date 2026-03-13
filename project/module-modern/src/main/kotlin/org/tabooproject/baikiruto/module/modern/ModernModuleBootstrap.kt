package org.tabooproject.baikiruto.module.modern

import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.Attributes
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.console
import taboolib.module.lang.sendLang

object ModernModuleBootstrap {

    @Awake(LifeCycle.ENABLE)
    private fun onEnable() {
        Attributes.factory = AttributeModifierFactoryModern
        Baikiruto.api().registerMetaFactory(ItemModelMetaFactoryModern)
        console().sendLang("log-modern-module-loaded")
    }
}
