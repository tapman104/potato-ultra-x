package com.potato.player.engine

import android.util.Log
import `is`.xyz.mpv.MPVLib
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class MpvCommandExecutor {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpv-engine-thread")
    }

    private val surfaceGeneration = AtomicInteger(0)
    private val pendingSeek       = AtomicReference<Double?>(null)

    fun execute(action: () -> Unit) {
        if (!executor.isShutdown) executor.submit(action)
    }

    fun nextSurfaceGeneration(): Int = surfaceGeneration.incrementAndGet()

    fun detachSurface() {
        val capturedGen = surfaceGeneration.get()
        execute {
            if (surfaceGeneration.get() == capturedGen) {
                Log.d(TAG, "detachSurface gen=$capturedGen")
                MPVLib.detachSurface()
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

    // Coalesced seek — only the last position queued before the thread picks it up is sent.
    // Used during scrubbing so hundreds of seeks don't back up on the queue.
    fun seekGesture(seconds: Double) {
        if (!seconds.isFinite()) return
        pendingSeek.set(seconds)
        execute {
            val target = pendingSeek.getAndSet(null) ?: return@execute
            MPVLib.command("seek", target.toString(), "absolute+keyframes")
        }
    }

    // Final precise seek on finger lift. Cancels any pending coalesced seek first.
    fun seekCommit(seconds: Double) {
        if (!seconds.isFinite()) return
        pendingSeek.set(null)
        execute { MPVLib.command("seek", seconds.toString(), "absolute+exact") }
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

    fun stop()     { execute { MPVLib.command("stop") } }
    fun shutdown() { executor.shutdown() }

    companion object { private const val TAG = "MpvCommandExecutor" }
}
