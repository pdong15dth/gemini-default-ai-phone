package com.aikeybridge.service

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSnifferTest {

    @Test
    fun patternIgnoresPidAndNumbers() {
        val pattern = LogSniffer.patternFor("I/SmartKey( 2255): ACTION_BUTTON_TO_START_FUNCTIONS id=42 t=1730000000")
        assertNotNull(pattern)
        val regex = Regex(pattern!!)
        assertTrue(regex.containsMatchIn("I/SmartKey( 1999): ACTION_BUTTON_TO_START_FUNCTIONS id=7 t=1731111111"))
    }

    @Test
    fun rejectsLinesThatAreNotBriefFormat() {
        assertNull(LogSniffer.patternFor("--------- beginning of main"))
    }
}
