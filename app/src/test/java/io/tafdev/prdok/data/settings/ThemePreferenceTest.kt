package io.tafdev.prdok.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun `system follows the device, the other two ignore it`() {
        assertTrue(ThemePreference.SYSTEM.isDark(systemInDarkTheme = true))
        assertFalse(ThemePreference.SYSTEM.isDark(systemInDarkTheme = false))

        assertTrue(ThemePreference.DARK.isDark(systemInDarkTheme = false))
        assertFalse(ThemePreference.LIGHT.isDark(systemInDarkTheme = true))
    }

    @Test
    fun `stored values are the lowercased names`() {
        assertEquals("system", ThemePreference.SYSTEM.stored)
        assertEquals("light", ThemePreference.LIGHT.stored)
        assertEquals("dark", ThemePreference.DARK.stored)
    }

    @Test
    fun `an unset or unknown stored value reads as system`() {
        assertEquals(ThemePreference.DARK, ThemePreference.fromStored("dark"))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStored("sepia"))
    }
}
