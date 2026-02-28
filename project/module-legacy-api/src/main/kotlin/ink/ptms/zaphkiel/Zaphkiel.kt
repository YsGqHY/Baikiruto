package ink.ptms.zaphkiel

import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.BaikirutoAPI

@Deprecated("Use org.tabooproject.baikiruto.core.Baikiruto#api()")
object Zaphkiel {

    fun api(): BaikirutoAPI {
        return Baikiruto.api()
    }
}
