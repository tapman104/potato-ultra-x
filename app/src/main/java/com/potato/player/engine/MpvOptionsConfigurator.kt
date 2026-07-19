package com.potato.player.engine

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib

class MpvOptionsConfigurator {

    fun copyFontAssets(context: Context) {
        val fontsDir = java.io.File(context.filesDir, "fonts")
        if (!fontsDir.exists()) fontsDir.mkdirs()
        val fontFile = java.io.File(fontsDir, "Roboto-Regular.ttf")
        if (!fontFile.exists()) {
            try {
                context.assets.open("Roboto-Regular.ttf").use { input ->
                    fontFile.outputStream().use { input.copyTo(it) }
                }
                Log.d(TAG, "Font asset copied")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy font asset", e)
            }
        }
    }

    fun initOptions(context: Context) {
        val filesDir = context.filesDir.path

        // Core engine config
        MPVLib.setOptionString("config",       "yes")
        MPVLib.setOptionString("config-dir",   filesDir)
        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle",         "once")

        // Video output
        MPVLib.setOptionString("profile",      "fast")
        MPVLib.setOptionString("vo",           "gpu")
        MPVLib.setOptionString("gpu-context",  "android")

        // Hardware decoding: HW+ → HW → SW fallback chain
        MPVLib.setOptionString("hwdec",        "mediacodec,mediacodec-copy,no")
        MPVLib.setOptionString("hwdec-codecs", "all")

        // Cache — capped for mobile memory
        MPVLib.setOptionString("demuxer-max-bytes",      "50MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes", "20MiB")
        MPVLib.setOptionString("cache-secs",             "30")

        // Logging — keep quiet in production
        MPVLib.setOptionString("msg-level", "all=warn")

        // Rendering optimizations
        MPVLib.setOptionString("vd-lavc-dr",          "yes")
        MPVLib.setOptionString("opengl-pbo",          "yes")
        MPVLib.setOptionString("opengl-early-flush",  "no")
        MPVLib.setOptionString("video-sync",          "audio")
        MPVLib.setOptionString("scale",               "bilinear")
        MPVLib.setOptionString("cscale",              "bilinear")
        MPVLib.setOptionString("dscale",              "bilinear")

        // Subtitle defaults — minimal setup, no auto-selection
        MPVLib.setOptionString("sub-font-provider", "none")
        MPVLib.setOptionString("sub-fonts-dir",     "$filesDir/fonts")
        MPVLib.setOptionString("sub-font",          "Roboto")
        MPVLib.setOptionString("sub-font-size",     "55")
        MPVLib.setOptionString("sub-bold",          "yes")
        MPVLib.setOptionString("sub-color",         "#FFFFFF")
        MPVLib.setOptionString("sub-border-color",  "#000000")
        MPVLib.setOptionString("sub-border-size",   "3")
        MPVLib.setOptionString("sub-auto",          "no")

        // Audio
        MPVLib.setOptionString("audio-pitch-correction", "yes")

        // Behaviour
        MPVLib.setPropertyBoolean("keep-open",              true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)
    }

    fun postInitOptions() {
        // Debanding off by default — can be toggled later in Phase 8
        MPVLib.setOptionString("deband", "no")
    }

    fun registerPropertyObservers() {
        MPVLib.observeProperty(MpvProp.PAUSE,              MpvFmt.FLAG)
        MPVLib.observeProperty(MpvProp.TIME_POS,           MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DURATION,           MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.DEMUXER_CACHE_TIME, MpvFmt.DOUBLE)
        MPVLib.observeProperty(MpvProp.HWDEC_CURRENT,      MpvFmt.STRING)
    }

    companion object { private const val TAG = "MpvOptionsConfigurator" }
}
