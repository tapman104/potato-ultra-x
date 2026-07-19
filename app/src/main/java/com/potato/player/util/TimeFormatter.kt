package com.potato.player.util

object TimeFormatter {
    /** Formats milliseconds as MM:SS or HH:MM:SS if >= 1 hour. */
    fun formatMs(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSec = ms / 1000L
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }
}
