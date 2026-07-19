package com.potato.player.engine

internal object MpvProp {
    const val PAUSE              = "pause"
    const val TIME_POS           = "time-pos"
    const val DURATION           = "duration"
    const val DEMUXER_CACHE_TIME = "demuxer-cache-time"
}

internal object MpvFmt {
    const val FLAG   = 3
    const val DOUBLE = 5
}

internal object MpvEvent {
    const val FILE_LOADED      = 8
    const val END_FILE         = 7
    const val PLAYBACK_RESTART = 15
}
