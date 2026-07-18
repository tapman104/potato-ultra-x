package com.tapman104.mpvplayer.util

object TimeFormatter {
    fun formatMs(ms: Long): String {
        if (ms < 0L) return "0:00"
        val totalSeconds = ms / 1000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0)
            "%d:%02d:%02d".format(hours, minutes, seconds)
        else
            "%d:%02d".format(minutes, seconds)
    }
}
