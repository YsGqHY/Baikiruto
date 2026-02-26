package org.tabooproject.baikiruto.impl

import org.tabooproject.baikiruto.core.Baikiruto
import taboolib.common.util.unsafeLazy

/**
 * Baikiruto
 * org.tabooproject.baikiruto.impl.DefaultBaikirutoBooster
 *
 * @author mical
 * @since 2026/2/26 23:06
 */
object DefaultBaikirutoBooster {

    val api by unsafeLazy { DefaultBaikirutoAPI() }

    fun startup() {
        Baikiruto.register(api)
    }
}