package com.potato.player.engine

internal object MpvProp {
    const val PAUSE              = "pause"
    const val TIME_POS           = "time-pos"
    const val DURATION           = "duration"
    const val DEMUXER_CACHE_TIME = "demuxer-cache-time"
    const val HWDEC              = "hwdec"
    const val HWDEC_CURRENT      = "hwdec-current"
}

internal object MpvFmt {
    const val FLAG   = 3
    const val STRING = 4
    const val DOUBLE = 5
}

internal object MpvEvent {
    const val FILE_LOADED      = 8
    const val END_FILE         = 7
    const val PLAYBACK_RESTART = 15
}
