package org.tabooproject.baikiruto.impl.hook

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeadDatabaseHookTest {

    @Test
    fun `should copy skull profile via accessors`() {
        val source = AccessorMeta(TestProfile("hdb-profile"))
        val target = AccessorMeta(null)

        val copied = SkullProfileBridge.copy(source, target)

        assertTrue(copied)
        assertEquals("hdb-profile", target.currentProfile()?.value)
    }

    @Test
    fun `should copy skull profile via fields`() {
        val source = FieldMeta().applyProfile(TestProfile("field-profile"))
        val target = FieldMeta()

        val copied = SkullProfileBridge.copy(source, target)

        assertTrue(copied)
        assertEquals("field-profile", target.currentProfile()?.value)
    }

    private data class TestProfile(val value: String)

    private class AccessorMeta(private var profile: TestProfile?) {
        fun getProfile(): TestProfile? {
            return profile
        }

        fun setProfile(profile: TestProfile) {
            this.profile = profile
        }

        fun currentProfile(): TestProfile? {
            return profile
        }
    }

    private open class FieldMeta {
        private var profile: TestProfile? = null

        fun applyProfile(profile: TestProfile): FieldMeta {
            val field = FieldMeta::class.java.getDeclaredField("profile")
            field.isAccessible = true
            field.set(this, profile)
            return this
        }

        fun currentProfile(): TestProfile? {
            val field = FieldMeta::class.java.getDeclaredField("profile")
            field.isAccessible = true
            return field.get(this) as? TestProfile
        }
    }
}
