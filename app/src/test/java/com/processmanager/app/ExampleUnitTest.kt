package com.processmanager.app

import org.junit.Test

import org.junit.Assert.*

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testMemoryFormatting() {
        val bytes = 1024L * 1024L * 100L
        assertTrue(bytes > 0)
    }
}
