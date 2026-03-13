package org.tabooproject.baikiruto.impl.item

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.tabooproject.baikiruto.core.Baikiruto
import org.tabooproject.baikiruto.core.item.ItemScriptHooks
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.console
import taboolib.module.lang.sendLang

object DefaultItemBootstrap {

    private const val DEBUG_ITEM_ID = "debug:stone"

    @Awake(LifeCycle.ENABLE)
    private fun init() {
        val api = Baikiruto.api()
        api.registerMetaFactory(ScriptMetaFactory)
        if (api.getItem(DEBUG_ITEM_ID) != null) {
            return
        }
        val item = api.registerItem(
            DefaultItem(
                id = DEBUG_ITEM_ID,
                template = ItemStack(Material.STONE),
                versionHashSupplier = { "m0-dev" },
                scripts = ItemScriptHooks(
                    // 脚本内嵌消息无法使用 lang 系统，此为 debug 物品的硬编码脚本
                    build = """
                        if (ctx["debug"] == true) {
                            sender?.sendMessage("[Baikiruto] build hook invoked for ${'$'}itemId")
                        }
                        return item
                    """.trimIndent()
                )
            )
        )
        ItemScriptPreheatService.preheatIfEnabled(listOf(item))
        console().sendLang("log-debug-item-registered", DEBUG_ITEM_ID)
    }
}
