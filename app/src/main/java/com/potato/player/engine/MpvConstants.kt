package com.potato.player.engine

internal object MpvProp {
    const val PAUSE              = "pause"
    const val TIME_POS           = "time-pos"
    const val DURATION           = "duration"
    const val DEMUXER_CACHE_TIME = "demuxer-cache-time"
    const val DEMUXER_CACHE_DURATION = "demuxer-cache-duration"
    const val SPEED              = "speed"
    const val HWDEC              = "hwdec"
    const val HWDEC_CURRENT      = "hwdec-current"
    const val TRACK_LIST_COUNT   = "track-list/count"
    const val AID                = "aid"
    const val SID                = "sid"
    const val SUB_SCALE          = "sub-scale"
    const val SUB_POS            = "sub-pos"
    const val VIDEO_PARAMS_W     = "video-params/w"
    const val VIDEO_PARAMS_H     = "video-params/h"
}

internal object MpvFmt {
    const val FLAG   = 3
    const val STRING = 4
    const val DOUBLE = 5
    const val INT64  = 6
}

internal object MpvEvent {
    const val FILE_LOADED      = 8
    const val END_FILE         = 7
    const val PLAYBACK_RESTART = 15
    const val VIDEO_RECONFIG   = 17
    const val SEEK             = 20
}
