package com.potato.player.engine

import android.util.Log
import `is`.xyz.mpv.MPVLib
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MpvCommandExecutor {

    @Volatile private var engineThread: Thread? = null

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpv-engine-thread").also { engineThread = it }
    }

    private val surfaceGeneration = AtomicInteger(0)
    private val pendingSeek       = AtomicReference<Double?>(null)

    fun execute(action: () -> Unit) {
        if (!executor.isShutdown) executor.submit(action)
    }

    fun nextSurfaceGeneration(): Int = surfaceGeneration.incrementAndGet()
    fun isCurrentSurfaceGeneration(gen: Int): Boolean = surfaceGeneration.get() == gen

    fun detachSurface() {
        val capturedGen = surfaceGeneration.incrementAndGet()
        execute {
            if (surfaceGeneration.get() == capturedGen) {
                Log.d(TAG, "detachSurface gen=$capturedGen")
                runCatching { MPVLib.setPropertyString("vo", "null") }
                runCatching { MPVLib.setPropertyString("force-window", "no") }
                runCatching { MPVLib.detachSurface() }
            } else {
                Log.d(TAG, "detachSurface skipped — stale gen=$capturedGen")
            }
        }
    }

    fun play()       { execute { MPVLib.setPropertyBoolean("pause", false) } }
    fun pause()      { execute { MPVLib.setPropertyBoolean("pause", true)  } }
    fun togglePlay() {
        execute {
            val paused = MPVLib.getPropertyBoolean("pause") ?: false
            MPVLib.setPropertyBoolean("pause", !paused)
        }
    }

    // Coalesced seek — atomically stores the latest position without hitting JNI.
    // The exact seek is committed instantly on finger lift via seekCommit.
    fun seekGesture(seconds: Double) {
        if (!seconds.isFinite()) return
        pendingSeek.set(seconds)
    }

    // Final precise seek on finger lift. Clears pending seek and executes exact commit on the single JNI thread.
    fun seekCommit(seconds: Double) {
        if (!seconds.isFinite()) return
        execute {
            pendingSeek.set(null)
            MPVLib.command("seek", seconds.toString(), "absolute+exact")
        }
    }

    fun seekExactRelative(offsetSec: Int) {
        execute {
            pendingSeek.set(null)
            Log.d(TAG, "seekExactRelative: $offsetSec")
            MPVLib.command("seek", offsetSec.toString(), "relative+exact")
        }
    }

    fun loadFile(path: String) {
        execute {
            MPVLib.command("loadfile", path, "replace")
            MPVLib.setPropertyString("demuxer-max-bytes",      "50MiB")
            MPVLib.setPropertyString("demuxer-max-back-bytes", "20MiB")
        }
    }

    fun setDecoder(hwdec: String) {
        execute {
            Log.d(TAG, "setDecoder: $hwdec")
            MPVLib.setPropertyString("hwdec", hwdec)
        }
    }

    fun setPlaybackSpeed(speed: Double) {
        execute {
            Log.d(TAG, "setPlaybackSpeed: $speed")
            MPVLib.setPropertyString("speed", speed.toString())
        }
    }

    fun getPropertyInt(name: String): Int? {
        if (executor.isShutdown) return null
        return try {
            if (Thread.currentThread() == engineThread) {
                MPVLib.getPropertyInt(name)
            } else {
                executor.submit<Int?> { MPVLib.getPropertyInt(name) }.get()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPropertyInt error for $name", e)
            null
        }
    }

    fun getPropertyString(name: String): String? {
        if (executor.isShutdown) return null
        return try {
            if (Thread.currentThread() == engineThread) {
                MPVLib.getPropertyString(name)
            } else {
                executor.submit<String?> { MPVLib.getPropertyString(name) }.get()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPropertyString error for $name", e)
            null
        }
    }

    fun setAudioTrack(id: Int) {
        execute { MPVLib.setPropertyString("aid", id.toString()) }
    }

    fun setSubtitleTrack(id: Int) {
        execute {
            val valStr = if (id == -1) "no" else id.toString()
            MPVLib.setPropertyString("sid", valStr)
        }
    }

    fun addExternalSubtitle(path: String, onAdded: () -> Unit = {}) {
        execute {
            MPVLib.command("sub-add", path, "select")
            onAdded()
        }
    }

    fun stop()     { execute { MPVLib.command("stop") } }
    fun shutdown() { executor.shutdown() }

    companion object { private const val TAG = "MpvCommandExecutor" }
}
