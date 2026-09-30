package com.caloriecompanion.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class NamesTest {
    @Test
    fun `names are trimmed, collapsed and lowercased`() {
        assertEquals("milk", normalizeName("  Milk "))
        assertEquals("whole milk", normalizeName("Whole \t  MILK"))
        assertEquals("brød", normalizeName("Brød"))
    }
}
