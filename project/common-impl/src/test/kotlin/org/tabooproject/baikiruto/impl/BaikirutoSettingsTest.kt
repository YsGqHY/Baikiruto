package org.tabooproject.baikiruto.impl

import kotlin.test.Test
import kotlin.test.assertTrue
import org.tabooproject.baikiruto.core.item.DisplayTextPolicy
import org.tabooproject.baikiruto.impl.item.LegacyTextColorizer

class BaikirutoSettingsTest {

    @Test
    fun `should preserve unknown angle tags when minimessage config enabled even if runtime unavailable`() {
        val previousEnabled = BaikirutoSettings.miniMessageEnabled
        val previousPolicy = DisplayTextPolicy.preserveUnknownAngleTags
        try {
            LegacyTextColorizer.setMiniMessageAvailabilityOverride(false)
            BaikirutoSettings.miniMessageEnabled = true

            val method = BaikirutoSettings::class.java.getDeclaredMethod("syncDisplayTextPolicy")
            method.isAccessible = true
            method.invoke(BaikirutoSettings)

            assertTrue(DisplayTextPolicy.preserveUnknownAngleTags)
        } finally {
            BaikirutoSettings.miniMessageEnabled = previousEnabled
            DisplayTextPolicy.preserveUnknownAngleTags = previousPolicy
            LegacyTextColorizer.clearMiniMessageOverrides()
        }
    }
}
