package com.potato.player.engine

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import `is`.xyz.mpv.MPVLib
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MpvSurface(private val executor: MpvCommandExecutor) : SurfaceHolder.Callback {

    @Volatile private var attachedSurface: Surface? = null
    private val pendingAttachSurface = AtomicReference<Surface?>(null)
    private var surfaceReadyCallback: (() -> Unit)? = null
    /** Invoked on main thread when the surface re-attaches after a background/resume cycle.
     *  Unlike surfaceReadyCallback, this does NOT trigger loadFile(). */
    @Volatile private var onSurfaceReattached: (() -> Unit)? = null
    private var lastHolderSurface: Surface? = null
    val isRotating = AtomicBoolean(false)
    /** True only after MPVLib.attachSurface() + vo=gpu have been confirmed on the engine thread
     *  for the current surface. Cleared whenever the app goes to background (via invalidateRenderState)
     *  or the surface is destroyed (via detachAndDisableVo), so that the next ON_RESUME always
     *  re-runs the full attach sequence regardless of whether the Surface object survived. */
    @Volatile private var isMpvRendering = false

    fun setSurfaceReadyCallback(cb: (() -> Unit)?) { surfaceReadyCallback = cb }
    fun setSurfaceReattachedCallback(cb: (() -> Unit)?) { onSurfaceReattached = cb }
    fun hasSurface(): Boolean =
        attachedSurface != null || pendingAttachSurface.get() != null
    /** Returns true only when the surface is fully attached and ready — NOT when merely pending. */
    fun hasAttachedSurface(): Boolean = attachedSurface != null
    /** Called on every app-background / lock so the next ON_RESUME forces a full re-attach even
     *  on devices where the Surface object survives and surfaceCreated() never fires. */
    fun invalidateRenderState() { isMpvRendering = false }


    override fun surfaceCreated(holder: SurfaceHolder) {
        lastHolderSurface = holder.surface
        if (isRotating.get()) {
            val surface = holder.surface
            val rect = holder.surfaceFrame
            val width = rect.width()
            val height = rect.height()
            val size = if (width > 0 && height > 0) "${width}x${height}" else ""
            attachedSurface = surface
            pendingAttachSurface.set(surface)
            executor.execute {
                if (!executor.isAlive()) return@execute
                runCatching { MPVLib.attachSurface(surface) }
                if (size.isNotEmpty()) {
                    runCatching { MPVLib.setOptionString("android-surface-size", size) }
                }
                runCatching { MPVLib.setOptionString("force-window", "yes") }
                runCatching { MPVLib.setPropertyString("vo", "gpu") }
                pendingAttachSurface.set(null)
            }
            isRotating.set(false)
            return
        }
        attachSurfaceInternal(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d(TAG, "surfaceChanged ${width}x${height}")
        lastHolderSurface = holder.surface
        attachSurfaceInternal(holder.surface)
        if (width > 0 && height > 0) {
            val size = "${width}x${height}"
            executor.execute {
                if (!executor.isAlive()) return@execute
                runCatching { MPVLib.setPropertyString("android-surface-size", size) }
                runCatching { MPVLib.setOptionString("force-window", "yes") }
                runCatching { MPVLib.setPropertyString("vo", "gpu") }
            }
        }
    }

    fun detachAndDisableVo() {
        Log.d(TAG, "detachAndDisableVo")
        isMpvRendering = false   // GPU context is being torn down; must re-attach on next resume
        attachedSurface = null
        pendingAttachSurface.set(null)
        executor.execute {
            if (!executor.isAlive()) return@execute
            runCatching { MPVLib.setPropertyString("vo", "null") }
            runCatching { MPVLib.setPropertyString("force-window", "no") }
            runCatching { MPVLib.detachSurface() }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d(TAG, "surfaceDestroyed")
        lastHolderSurface = null
        if (isRotating.get()) {
            attachedSurface = null
            pendingAttachSurface.set(null)
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
        // Idempotency guard: if MPV is already confirmed rendering into this exact surface,
        // skip the re-attach. This prevents redundant GPU context setup when both the
        // surfaceCreated callback path and the ON_RESUME guard call this in the same frame.
        // isMpvRendering is always cleared on background/lock, so this will NOT skip the
        // attach on the lock→unlock or background→foreground paths.
        if (isMpvRendering && s == attachedSurface) return
        // During a decoder switch we only need to re-attach the surface to the GPU
        // output — we must NOT invoke surfaceReadyCallback because that would call
        // loadFile() and restart the video from the beginning.
        val gen = executor.nextSurfaceGeneration()
        attachedSurface = s
        pendingAttachSurface.set(s)
        executor.execute {
            if (!executor.isAlive()) return@execute
            if (executor.isCurrentSurfaceGeneration(gen)) {
                runCatching { MPVLib.attachSurface(s) }
                runCatching { MPVLib.setOptionString("force-window", "yes") }
                runCatching { MPVLib.setPropertyString("vo", "gpu") }
                pendingAttachSurface.set(null)
                // Confirm on main thread that MPV is now actively rendering.
                mainHandler.post { isMpvRendering = true }
            }
        }
    }


    private fun attachSurfaceInternal(surface: Surface?) {
        if (surface == null || !surface.isValid || surface == attachedSurface) return
        attachedSurface = surface
        pendingAttachSurface.set(surface)
        val gen             = executor.nextSurfaceGeneration()
        val readyCb         = surfaceReadyCallback
        val reattachCb      = onSurfaceReattached
        executor.execute {
            if (!executor.isAlive()) return@execute
            if (executor.isCurrentSurfaceGeneration(gen)) {
                Log.d(TAG, "attachSurface gen=$gen")
                runCatching { MPVLib.attachSurface(surface) }
                runCatching { MPVLib.setOptionString("force-window", "yes") }
                runCatching { MPVLib.setPropertyString("vo", "gpu") }
                pendingAttachSurface.set(null)
                mainHandler.post {
                    when {
                        readyCb   != null -> readyCb()    // first load — call loadFile()
                        reattachCb != null -> {            // resume — mark rendering live, then notify
                            isMpvRendering = true
                            reattachCb()
                        }
                        // else: no callback registered yet; ON_RESUME guard in PlayerScreen covers this
                    }
                }
            } else {
                Log.d(TAG, "attachSurface skipped — stale gen=$gen")
            }
        }
    }

    companion object {
        private const val TAG = "MpvSurface"
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
