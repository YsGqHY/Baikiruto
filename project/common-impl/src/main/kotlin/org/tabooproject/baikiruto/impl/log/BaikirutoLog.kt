package org.tabooproject.baikiruto.impl.log

import taboolib.common.platform.function.severe
import taboolib.common.platform.function.warning
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 错误日志工具。
 * 这些方法输出到服务器控制台日志，保留原生日志级别（SEVERE/WARNING）以便日志聚合工具过滤。
 * 有意不使用 lang 系统——错误诊断信息需要固定格式，且日志级别语义优先于国际化。
 */
object BaikirutoLog {

    private val fluxonBootstrapLogged = AtomicBoolean(false)

    fun serviceMissing(service: String, throwable: Throwable) {
        severe("[Baikiruto][SERVICE_MISSING] $service -> ${throwable.message}")
    }

    fun scriptCompileFailed(scriptId: String, throwable: Throwable) {
        warning("[Baikiruto][SCRIPT_COMPILE_FAILED] $scriptId -> ${throwable.message}")
    }

    fun scriptRuntimeFailed(scriptId: String, throwable: Throwable) {
        warning("[Baikiruto][SCRIPT_RUNTIME_FAILED] $scriptId -> ${throwable.message}")
    }

    fun fluxonBootstrapFailed(message: String) {
        if (fluxonBootstrapLogged.compareAndSet(false, true)) {
            System.err.println("[Baikiruto][FLUXON_BOOTSTRAP_FAILED] $message")
        }
    }
}
