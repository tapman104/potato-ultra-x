package com.potato.player.engine

import android.content.Context
import android.util.Log
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.concurrent.atomic.AtomicBoolean

sealed class InitResult {
    object Success : InitResult()
    data class Failure(val message: String) : InitResult()
}

class MpvEngine(private val context: Context) {

    val executor     = MpvCommandExecutor()
    val dispatcher   = MpvEventDispatcher()
    val surface      = MpvSurface(executor)
    val configurator = MpvOptionsConfigurator()

    private val _initResult = MutableSharedFlow<InitResult>(replay = 1)
    val initResult: SharedFlow<InitResult> = _initResult

    private val initialized = AtomicBoolean(false)

    fun init() {
        if (!initialized.compareAndSet(false, true)) return
        Log.d(TAG, "init")

        executor.execute {
            try {
                configurator.copyFontAssets(context)
                MPVLib.create(context.applicationContext)
                configurator.initOptions(context)
                MPVLib.init()

                // Re-assert cache caps post-init (some builds reset on init)
                MPVLib.setPropertyString("demuxer-max-bytes",      "50MiB")
                MPVLib.setPropertyString("demuxer-max-back-bytes", "20MiB")
                MPVLib.setPropertyString("cache-secs",             "30")

                configurator.postInitOptions()
                MPVLib.addObserver(dispatcher)
                configurator.registerPropertyObservers()

                _initResult.tryEmit(InitResult.Success)
                Log.d(TAG, "init complete")
            } catch (e: Exception) {
                initialized.set(false)
                Log.e(TAG, "init failed", e)
                _initResult.tryEmit(InitResult.Failure(e.message ?: "Unknown error"))
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun destroy() {
        if (!initialized.compareAndSet(true, false)) return
        Log.d(TAG, "destroy")

        _initResult.resetReplayCache()
        executor.detachSurface()

        // Submit destroy task BEFORE shutdown so it actually runs
        executor.execute {
            runCatching { MPVLib.removeObserver(dispatcher) }
            runCatching { MPVLib.destroy() }
            Log.d(TAG, "destroy complete")
        }

        // shutdown AFTER submitting — never call shutdown() inside an execute{} block
        executor.shutdown()
    }

    companion object { private const val TAG = "MpvEngine" }
}
