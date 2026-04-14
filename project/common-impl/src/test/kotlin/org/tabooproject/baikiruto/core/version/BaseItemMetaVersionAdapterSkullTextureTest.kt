package org.tabooproject.baikiruto.core.version

import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.profile.PlayerProfile
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BaseItemMetaVersionAdapterSkullTextureTest {

    private val adapter = object : BaseItemMetaVersionAdapter() {}

    @Test
    fun `should apply owner profile through skull setter`() {
        var appliedProfile: Any? = null
        val meta = Proxy.newProxyInstance(
            SkullMeta::class.java.classLoader,
            arrayOf(SkullMeta::class.java)
        ) { _, method, args ->
            when (method.name) {
                "setOwnerProfile" -> {
                    appliedProfile = args?.firstOrNull()
                    null
                }
                "getOwnerProfile" -> appliedProfile
                else -> defaultValue(method.returnType)
            }
        } as SkullMeta

        val profile = Proxy.newProxyInstance(
            PlayerProfile::class.java.classLoader,
            arrayOf(PlayerProfile::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getUniqueId" -> UUID.randomUUID()
                "getName" -> "baikiruto"
                "serialize" -> emptyMap<String, Any>()
                else -> defaultValue(method.returnType)
            }
        } as PlayerProfile

        val method = BaseItemMetaVersionAdapter::class.java.getDeclaredMethod(
            "applySkullProfileValue",
            ItemMeta::class.java,
            Any::class.java
        )
        method.isAccessible = true
        val result = method.invoke(adapter, meta, profile) as Boolean

        assertTrue(result)
        assertSame(profile, appliedProfile)
    }

    private fun defaultValue(type: Class<*>): Any? {
        return when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0f
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
