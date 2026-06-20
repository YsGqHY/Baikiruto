package org.tabooproject.baikiruto.impl.hook

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.mockito.Mockito
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

    @Test
    fun `should restore missing bind lore from source snapshot`() {
        val rebuilt = mockItemStackWithLore(listOf("&7Baikiruto Lore"))

        val result = GuibindProHook.restoreBoundLoreForTesting(rebuilt, "§a已绑定: Steve")

        assertEquals(listOf("§a已绑定: Steve", "&7Baikiruto Lore"), result.itemMeta?.lore)
    }

    @Test
    fun `should replace existing bind lore placeholder instead of duplicating`() {
        val rebuilt = mockItemStackWithLore(listOf("&7Baikiruto Lore", "§c绑定者: Alex"))

        val result = GuibindProHook.restoreBoundLoreForTesting(rebuilt, "§a已绑定: Steve", bindLoreIndex = 1)

        assertEquals(listOf("&7Baikiruto Lore", "§a已绑定: Steve"), result.itemMeta?.lore)
    }

    private fun mockItemStackWithLore(initialLore: List<String>): ItemStack {
        val itemStack = Mockito.mock(ItemStack::class.java)
        var meta = mockItemMeta(initialLore)
        Mockito.`when`(itemStack.itemMeta).thenAnswer { meta }
        Mockito.`when`(itemStack.setItemMeta(Mockito.any(ItemMeta::class.java))).thenAnswer { invocation ->
            meta = invocation.arguments[0] as ItemMeta
            true
        }
        return itemStack
    }

    private fun mockItemMeta(initialLore: List<String>): ItemMeta {
        val itemMeta = Mockito.mock(ItemMeta::class.java)
        var lore: List<String>? = initialLore
        Mockito.`when`(itemMeta.lore).thenAnswer { lore }
        Mockito.doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            lore = invocation.arguments[0] as? List<String>
            null
        }.`when`(itemMeta).lore = Mockito.anyList()
        return itemMeta
    }
}
