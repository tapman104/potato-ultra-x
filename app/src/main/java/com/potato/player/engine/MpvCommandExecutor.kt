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

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "mpv-seek-scheduler").also { it.isDaemon = true }
    }
    @Volatile private var lastSeekTimeMs = 0L
    @Volatile private var isSeekScheduled = false

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

    // Debounced seek — rate limits approximate keyframe seeks to eliminate decoder lag/stuttering during scrubbing
    fun seekGesture(seconds: Double) {
        if (!seconds.isFinite()) return
        pendingSeek.set(seconds)
        scheduleOrExecuteSeek()
    }

    @Synchronized
    private fun scheduleOrExecuteSeek() {
        if (isSeekScheduled) return
        val now = System.currentTimeMillis()
        val elapsed = now - lastSeekTimeMs
        val throttleMs = 120L
        if (elapsed >= throttleMs) {
            isSeekScheduled = true
            execute {
                isSeekScheduled = false
                val target = pendingSeek.getAndSet(null) ?: return@execute
                lastSeekTimeMs = System.currentTimeMillis()
                MPVLib.command("seek", target.toString(), "absolute+keyframes")
            }
        } else {
            isSeekScheduled = true
            val delayMs = throttleMs - elapsed
            scheduler.schedule({
                isSeekScheduled = false
                val target = pendingSeek.getAndSet(null) ?: return@schedule
                execute {
                    lastSeekTimeMs = System.currentTimeMillis()
                    MPVLib.command("seek", target.toString(), "absolute+keyframes")
                }
            }, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    // Final precise seek on finger lift. Cancels any pending throttled seek first.
    fun seekCommit(seconds: Double) {
        if (!seconds.isFinite()) return
        pendingSeek.set(null)
        lastSeekTimeMs = 0L
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
    fun shutdown() {
        scheduler.shutdownNow()
        executor.shutdown()
    }

    companion object { private const val TAG = "MpvCommandExecutor" }
}
