package com.feerlessleadr.downbeat.playback

/**
 * Lets a command through only if the same command was not accepted within the last [windowMs]. Some phones' media
 * players trigger one tap twice (once through the notification button and once through the media session), which made
 * "next" skip twice. A different command is never held up by another one.
 */
class CommandDebouncer(private val windowMs: Long = 900, private val clock: () -> Long) {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun accept(name: String): Boolean {
        val now = clock()
        val previous = last[name]
        if (previous != null && now - previous < windowMs) return false
        last[name] = now
        return true
    }
}
