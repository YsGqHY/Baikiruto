package org.tabooproject.baikiruto.impl.log

import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning

object BaikirutoLog {

    fun serviceMissing(service: String, throwable: Throwable) {
        severe("[Baikiruto][SERVICE_MISSING] $service -> ${throwable.message}")
    }

    fun scriptCompileFailed(scriptId: String, throwable: Throwable) {
        warning("[Baikiruto][SCRIPT_COMPILE_FAILED] $scriptId -> ${throwable.message}")
    }

    fun scriptRuntimeFailed(scriptId: String, throwable: Throwable) {
        warning("[Baikiruto][SCRIPT_RUNTIME_FAILED] $scriptId -> ${throwable.message}")
    }
}
