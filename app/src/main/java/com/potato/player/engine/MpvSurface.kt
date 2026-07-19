package com.potato.player.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import `is`.xyz.mpv.MPVLib
import java.util.concurrent.atomic.AtomicReference

class MpvSurface(private val executor: MpvCommandExecutor) : SurfaceHolder.Callback {

    private var attachedSurface: Surface? = null
    private val pendingAttachSurface = AtomicReference<Surface?>(null)
    private var surfaceReadyCallback: (() -> Unit)? = null

    fun setSurfaceReadyCallback(cb: (() -> Unit)?) { surfaceReadyCallback = cb }
    fun hasSurface(): Boolean =
        attachedSurface != null || pendingAttachSurface.get() != null

    override fun surfaceCreated(holder: SurfaceHolder) {
        attachSurfaceInternal(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged ${width}x${height}")
        attachSurfaceInternal(holder.surface)
        if (width > 0 && height > 0) {
            val size = "${width}x${height}"
            executor.execute {
                runCatching { MPVLib.setPropertyString("android-surface-size", size) }
                runCatching { MPVLib.setPropertyString("vo", "gpu") }
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        attachedSurface = null
        pendingAttachSurface.set(null)
        // Disable VO before detach — prevents MPV rendering to a dead surface
        executor.execute {
            runCatching { MPVLib.setPropertyString("vo", "null") }
            runCatching { MPVLib.setPropertyString("force-window", "no") }
        }
        executor.detachSurface()
    }

    private fun attachSurfaceInternal(surface: Surface?) {
        if (surface == null || !surface.isValid || surface == attachedSurface) return
        attachedSurface = surface
        pendingAttachSurface.set(surface)
        val gen      = executor.nextSurfaceGeneration()
        val callback = surfaceReadyCallback
        executor.execute {
            Log.d(TAG, "attachSurface gen=$gen")
            runCatching { MPVLib.attachSurface(surface) }
            runCatching { MPVLib.setOptionString("force-window", "yes") }
            runCatching { MPVLib.setPropertyString("vo", "gpu") }
            pendingAttachSurface.set(null)
            mainHandler.post { callback?.invoke() }
        }
    }

    companion object {
        private const val TAG = "MpvSurface"
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
