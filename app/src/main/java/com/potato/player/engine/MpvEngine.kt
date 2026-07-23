package com.potato.player.engine

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicBoolean


class MpvEngine(context: Context) {
    // ponytail: store applicationContext so passing an Activity never leaks
    val context: Context = context.applicationContext

    val executor     = MpvCommandExecutor()
    val dispatcher   = MpvEventDispatcher()
    val surface      = MpvSurface(executor, dispatcher)
    val configurator = MpvOptionsConfigurator()

    private val _initResult = MutableSharedFlow<Result<Unit>>(replay = 1)
    val initResult: SharedFlow<Result<Unit>> = _initResult

    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) {
            Log.w(TAG, "init() called on already-initialized engine — skipped")
            return
        }
        Log.d(TAG, "init")

        executor.execute {
            try {
                configurator.copyFontAssets(context)
                MPVLib.create(context.applicationContext)
                configurator.initOptions(context)
                MPVLib.init()
                executor.setAlive(true)

                // Re-assert cache caps post-init (some builds reset on init)
                MPVLib.setPropertyString("demuxer-max-bytes",      MpvCache.MAX_BYTES)
                MPVLib.setPropertyString("demuxer-max-back-bytes", MpvCache.MAX_BACK_BYTES)
                MPVLib.setPropertyString("cache-secs",             MpvCache.SECS)

                configurator.postInitOptions()
                MPVLib.addObserver(dispatcher)
                configurator.registerPropertyObservers()

                _initResult.tryEmit(Result.success(Unit))
                Log.d(TAG, "init complete")
            } catch (e: Exception) {
                initialized.set(false)
                executor.setAlive(false)
                Log.e(TAG, "init failed", e)
                _initResult.tryEmit(Result.failure(Exception(e.message ?: "Unknown error")))
            }
        }
    }

    fun prepareDecoderSwitch(onReady: () -> Unit) {
        // All supported decoders (no / mediacodec / mediacodec-copy) run under vo=gpu,
        // so hwdec switches are transparent to the video output — MPV reinitialises the
        // decoder on its own decode cycle without touching the surface.
        //
        // The previous detach+reattach dance caused a permanent black screen:
        //   detachAndDisableVo(), onReady() (setDecoder), and reattachSurface() were
        //   posted as three independent async tasks.  The latch in attachSequence fired
        //   on the VO-teardown VIDEO_RECONFIG (already consumed before the listener was
        //   even registered), then set vo=gpu — only for MPV's decoder-switch reconfig
        //   to fire immediately after and orphan the surface with vo=null while audio
        //   (running on an independent ao pipeline) stayed alive.
        onReady()
    }

    fun enterStandby() {
        if (!initialized.get() || !executor.isAlive()) return
        Log.d(TAG, "enterStandby")
        surface.detachAndDisableVo()
        executor.execute {
            if (!executor.isAlive()) return@execute
            runCatching { MPVLib.command("stop") }
            runCatching { MPVLib.setPropertyString("vo", "null") }
        }
    }


    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun destroy() {
        if (!initialized.compareAndSet(true, false)) {
            Log.w(TAG, "destroy() called on non-initialized engine — skipped")
            return
        }
        Log.d(TAG, "destroy")

        _initResult.resetReplayCache()
        executor.detachSurface()

        // Submit destroy task BEFORE shutdown so it actually runs
        executor.execute {
            if (executor.isAlive()) {
                executor.setAlive(false)
                runCatching { MPVLib.removeObserver(dispatcher) }
                runCatching { MPVLib.destroy() }
                Log.d(TAG, "destroy complete")
            }
        }

        // shutdown AFTER submitting — never call shutdown() inside an execute{} block
        executor.shutdown()
    }

    companion object { private const val TAG = "MpvEngine" }
}
