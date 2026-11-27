package com.feerlessleadr.downbeat.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDebouncerTest {
    private var now = 1_000L
    private val debouncer = CommandDebouncer(windowMs = 900) { now }

    @Test fun aRepeatInsideTheWindowIsDroppedAndOneAfterItIsAccepted() {
        assertTrue(debouncer.accept("next"))
        now += 200
        assertFalse(debouncer.accept("next"))
        now += 300
        assertFalse(debouncer.accept("next"))
        now += 500 // 1000 ms after the accepted one
        assertTrue(debouncer.accept("next"))
    }

    @Test fun differentCommandsDoNotBlockEachOther() {
        assertTrue(debouncer.accept("pause"))
        assertTrue(debouncer.accept("next"))
        now += 100
        assertTrue(debouncer.accept("play"))
        assertFalse(debouncer.accept("pause"))
    }

    @Test fun aDroppedRepeatDoesNotExtendTheWindow() {
        assertTrue(debouncer.accept("next"))
        now += 800
        assertFalse(debouncer.accept("next"))
        now += 150 // 950 ms after the accepted one, 150 after the dropped one
        assertTrue(debouncer.accept("next"))
    }
}
