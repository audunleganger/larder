package com.larder.shared

import com.larder.shared.domain.byName
import kotlin.test.Test
import kotlin.test.assertEquals

class NameOrderTest {
    @Test
    fun `names sort the Norwegian way`() {
        val names = listOf("Ål", "ørret", "Zucchini", "Ærfugl", "agurk", "Banan", "Øl", "æble", "Eple")
        assertEquals(
            listOf("agurk", "Banan", "Eple", "Zucchini", "æble", "Ærfugl", "Øl", "ørret", "Ål"),
            names.sortedWith(byName { it }),
        )
    }
}
