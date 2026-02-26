package org.tabooproject.baikiruto.module.bukkit

import org.tabooproject.baikiruto.impl.DefaultBaikirutoBooster
import taboolib.common.LifeCycle
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.disablePlugin
import taboolib.common.platform.function.registerLifeCycleTask

/**
 * 这是你的插件在 Bukkit 平台运行的基础
 * 一般情况下你不需要修改这个类
 */
object BaikirutoPlugin : Plugin() {

    init {
        registerLifeCycleTask(LifeCycle.INIT) {
            try {
                DefaultBaikirutoBooster.startup()
            } catch (ex: Throwable) {
                ex.printStackTrace()
                disablePlugin()
            }
        }
    }
}