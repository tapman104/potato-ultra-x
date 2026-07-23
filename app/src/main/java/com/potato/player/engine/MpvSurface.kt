package com.potato.player.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import `is`.xyz.mpv.MPVLib
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MpvSurface(
    private val executor: MpvCommandExecutor,
    private val dispatcher: MpvEventDispatcher,
) : SurfaceHolder.Callback {

    val isRotating = AtomicBoolean(false)
    // Tracks whether MPV currently has a live surface and is rendering video.
    // Set false on release, true after a successful reattach.
    val isMpvRendering = AtomicBoolean(false)

    private var surfaceReadyCallback: (() -> Unit)? = null
    @Volatile private var onSurfaceReattached: (() -> Unit)? = null
    private var lastHolderSurface: Surface? = null

    fun setSurfaceReadyCallback(cb: (() -> Unit)?) {
        surfaceReadyCallback = cb
    }

    fun setSurfaceReattachedCallback(cb: (() -> Unit)?) {
        onSurfaceReattached = cb
    }

    /** Called on onPause / entering background. Detaches the surface so the next
     *  resume always does a clean re-attach, even on devices where surfaceDestroyed
     *  never fires (e.g. lock-screen on MIUI / Samsung with surface surviving). */
    fun releaseForBackground() {
        isMpvRendering.set(false)
        executor.execute {
            if (!executor.isAlive()) return@execute
            runCatching { MPVLib.setPropertyString("vo", "null") }
            runCatching { MPVLib.detachSurface() }
        }
    }

    fun detachAndDisableVo() {
        executor.execute {
            if (!executor.isAlive()) return@execute
            runCatching { MPVLib.setPropertyString("vo", "null") }
            runCatching { MPVLib.setPropertyString("force-window", "no") }
            runCatching { MPVLib.detachSurface() }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        lastHolderSurface = holder.surface
        val isFirstLoad = !isRotating.get()
        isRotating.set(false)
        attachSequence(holder.surface, isFirstLoad)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        lastHolderSurface = holder.surface
        if (width > 0 && height > 0) {
            val size = "${width}x${height}"
            executor.execute {
                if (!executor.isAlive()) return@execute
                runCatching { MPVLib.setPropertyString("android-surface-size", size) }
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        lastHolderSurface = null
        if (isRotating.get()) {
            executor.execute {
                if (!executor.isAlive()) return@execute
                runCatching { MPVLib.detachSurface() }
            }
        } else {
            detachAndDisableVo()
        }
    }

    /** Re-attach to an existing surface after resume or decoder switch.
     *  Uses a fixed sleep instead of a VIDEO_RECONFIG latch because the event
     *  may already have fired before our listener is registered (OEM issue on
     *  MIUI, Samsung One UI, and others). */
    fun reattachSurface() {
        val s = lastHolderSurface
        if (s == null || !s.isValid) {
            Log.w(TAG, "reattachSurface: no valid surface — skipped")
            return
        }

        executor.execute {
            if (!executor.isAlive()) return@execute
            Log.d(TAG, "reattachSurface: resetting VO before re-attach")

            // Aggressive reset — required for OEMs that keep stale EGL state
            runCatching { MPVLib.setPropertyString("vo", "null") }
            runCatching { MPVLib.detachSurface() }
            runCatching { MPVLib.setOptionString("force-window", "no") }
            Thread.sleep(80) // let MPV's C-core process the teardown

            runCatching { MPVLib.attachSurface(s) }
            runCatching { MPVLib.setOptionString("force-window", "yes") }
            runCatching { MPVLib.setPropertyString("vo", "gpu") }

            Log.d(TAG, "reattachSurface: vo=gpu, firing callback")
            mainHandler.post {
                isMpvRendering.set(true)
                onSurfaceReattached?.invoke()
            }
        }
    }

    private fun attachSequence(surface: Surface, isFirstLoad: Boolean) {
        executor.execute {
            if (!executor.isAlive()) return@execute

            runCatching { MPVLib.setPropertyString("vo", "null") }
            runCatching { MPVLib.detachSurface() }

            val latch = CountDownLatch(1)
            val listener = object : MpvEventListener {
                override fun onVideoReconfig() {
                    latch.countDown()
                }
            }
            
            dispatcher.addListener(listener)
            try {
                latch.await(200, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                // Ignore
            } finally {
                dispatcher.removeListener(listener)
            }

            if (!executor.isAlive()) return@execute

            runCatching { MPVLib.attachSurface(surface) }
            runCatching { MPVLib.setOptionString("force-window", "yes") }
            runCatching { MPVLib.setPropertyString("vo", "gpu") }

            mainHandler.post {
                isMpvRendering.set(true)
                val readyCb = surfaceReadyCallback
                if (isFirstLoad) {
                    surfaceReadyCallback = null
                    readyCb?.invoke()
                } else {
                    onSurfaceReattached?.invoke()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MpvSurface"
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
