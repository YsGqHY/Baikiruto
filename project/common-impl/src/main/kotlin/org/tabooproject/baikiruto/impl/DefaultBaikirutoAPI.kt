package org.tabooproject.baikiruto.impl

import org.tabooproject.baikiruto.core.BaikirutoAPI
import org.tabooproject.baikiruto.core.BaikirutoScriptHandler
import taboolib.common.platform.PlatformFactory

/**
 * Baikiruto
 * org.tabooproject.baikiruto.impl.DefaultBaikirutoAPI
 *
 * @author mical
 * @since 2026/2/26 23:07
 */
class DefaultBaikirutoAPI : BaikirutoAPI {

    private val scriptHandler = PlatformFactory.getAPI<BaikirutoScriptHandler>()

    override fun getScriptHandler(): BaikirutoScriptHandler {
        return scriptHandler
    }
}