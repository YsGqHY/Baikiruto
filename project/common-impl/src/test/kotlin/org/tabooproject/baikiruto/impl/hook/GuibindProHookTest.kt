package org.tabooproject.baikiruto.impl.hook

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.tabooproject.baikiruto.impl.item.DefaultItemUpdater
import org.tabooproject.baikiruto.impl.item.ItemUpdateStatePreserver

class GuibindProHookTest {

    private lateinit var previousPreserver: ItemUpdateStatePreserver

    @BeforeEach
    fun setup() {
        previousPreserver = DefaultItemUpdater.installStatePreserverForTesting(GuibindProHook)
    }

    @AfterEach
    fun teardown() {
        DefaultItemUpdater.installStatePreserverForTesting(previousPreserver)
    }

    @Test
    fun `should return rebuilt when GuibindPro not available`() {
        // 当 GuibindPro 插件不存在时，hook 应该直接返回 rebuilt
        val source = ItemStack(Material.DIAMOND_SWORD)
        val rebuilt = ItemStack(Material.DIAMOND_SWORD)

        val result = GuibindProHook.preserve(source, rebuilt, null)

        // 由于测试环境中 Bukkit.getPluginManager() 返回 null，
        // hook 会直接返回 rebuilt 而不做任何处理
        assertSame(rebuilt, result)
    }

    @Test
    fun `should handle null ItemMeta gracefully`() {
        // 验证 hook 能够处理没有 ItemMeta 的物品
        val source = ItemStack(Material.AIR)
        val rebuilt = ItemStack(Material.DIAMOND_SWORD)

        val result = GuibindProHook.preserve(source, rebuilt, null)

        // 应该返回 rebuilt 而不抛异常
        assertNotNull(result)
    }

    @Test
    fun `should handle items without lore`() {
        // 验证 hook 能够处理没有 lore 的物品
        val source = ItemStack(Material.DIAMOND_SWORD)
        val rebuilt = ItemStack(Material.DIAMOND_SWORD)

        val result = GuibindProHook.preserve(source, rebuilt, null)

        // 应该返回 rebuilt 而不抛异常
        assertNotNull(result)
    }
}
