package com.potato.player.engine

import android.os.Handler
import android.os.Looper
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
    private var surfaceReadyCallback: (() -> Unit)? = null
    @Volatile private var onSurfaceReattached: (() -> Unit)? = null
    @Volatile private var lastHolderSurface: Surface? = null

    fun setSurfaceReadyCallback(cb: (() -> Unit)?) {
        surfaceReadyCallback = cb
    }

    fun setSurfaceReattachedCallback(cb: (() -> Unit)?) {
        onSurfaceReattached = cb
    }

    fun releaseForBackground() {
        detachAndDisableVo()
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

    fun reattachSurface() {
        val s = lastHolderSurface
        if (s == null || !s.isValid) return
        attachSequence(s, isFirstLoad = false)
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
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
