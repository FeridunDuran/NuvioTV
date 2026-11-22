package com.nuvio.tv.core.perf

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import androidx.core.view.doOnPreDraw

/**
 * Lightweight performance log for weak TVs. Read it with: adb logcat -s TurboPerf
 * Every 10 s: frames, late frames, worst frame, heap, native heap, PSS-free device memory.
 * Events: mark("name") prints a timestamped line. No URLs, titles or account data are logged.
 */
object TurboPerf {
    const val TAG = "TurboPerf"
    private const val REPORT_MS = 10_000L
    private const val LATE_FRAME_NS = 33_000_000L

    private val main = Handler(Looper.getMainLooper())
    private var app: Application? = null
    private var lastFrameNs = 0L
    private var frames = 0
    private var late = 0
    private var worstNs = 0L
    private var started = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNs != 0L) {
                val delta = frameTimeNanos - lastFrameNs
                // Long gaps with nothing to draw still count; the report shows the worst one.
                frames++
                if (delta > LATE_FRAME_NS) late++
                if (delta > worstNs) worstNs = delta
            }
            lastFrameNs = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val report = object : Runnable {
        override fun run() {
            logReport()
            main.postDelayed(this, REPORT_MS)
        }
    }

    fun start(application: Application) {
        if (started) return
        started = true
        app = application
        mark("process_start")
    }

    private var sampling = false

    // adb shell setprop log.tag.TurboPerf DEBUG; backgrounding always stops sampling.
    fun setForeground(foreground: Boolean) {
        val enable = foreground && Log.isLoggable(TAG, Log.DEBUG)
        if (sampling == enable) return
        sampling = enable
        pendingFocusSample = 0L
        main.removeCallbacks(report)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        lastFrameNs = 0L
        frames = 0
        late = 0
        worstNs = 0L
        if (enable) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            main.postDelayed(report, REPORT_MS)
        }
    }

    private var dispatchingDpad = false
    private var dpadEventTimeMs = 0L
    private var focusSample = 0L
    private var pendingFocusSample = 0L

    fun dispatchDpad(event: KeyEvent, dispatch: () -> Boolean): Boolean {
        if (!sampling || event.action != KeyEvent.ACTION_DOWN ||
            event.keyCode !in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
        ) return dispatch()

        dispatchingDpad = true
        dpadEventTimeMs = event.eventTime
        Trace.beginSection("Nuvio D-pad dispatch")
        return try {
            dispatch()
        } finally {
            Trace.endSection()
            dispatchingDpad = false
        }
    }

    fun onHomeCardFocused(view: View) {
        if (!dispatchingDpad || pendingFocusSample != 0L) return
        val inputTimeMs = dpadEventTimeMs
        val focusTimeMs = SystemClock.uptimeMillis()
        val sample = ++focusSample
        pendingFocusSample = sample
        view.doOnPreDraw {
            if (pendingFocusSample != sample) return@doOnPreDraw
            pendingFocusSample = 0L
            if (sampling) {
                Trace.beginSection("Nuvio Home focus pre-draw")
                Trace.endSection()
                Log.d(
                    TAG,
                    "event=home_focus_predraw inputToFocusMs=${focusTimeMs - inputTimeMs} " +
                        "focusToPreDrawMs=${SystemClock.uptimeMillis() - focusTimeMs}"
                )
            }
        }
        // A detached view loses its pre-draw listener. Clear the pending flag if no frame arrives.
        main.postDelayed({
            if (pendingFocusSample == sample) pendingFocusSample = 0L
        }, 500L)
    }

    fun onHeroBackdropLoaded() {
        if (!sampling) return
        Trace.beginSection("Nuvio Hero backdrop loaded")
        Trace.endSection()
        mark("hero_backdrop_loaded")
    }

    fun mark(name: String) {
        Log.i(TAG, "event=$name uptimeMs=${android.os.SystemClock.uptimeMillis()}")
    }

    private fun logReport() {
        val runtime = Runtime.getRuntime()
        val heapMb = (runtime.totalMemory() - runtime.freeMemory()) / MB
        val nativeMb = Debug.getNativeHeapAllocatedSize() / MB
        val memory = ActivityManager.MemoryInfo()
        val manager = app?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.getMemoryInfo(memory)
        Log.i(
            TAG,
            "frames=$frames late=$late worstMs=${worstNs / 1_000_000} heapMb=$heapMb nativeMb=$nativeMb " +
                "availMb=${memory.availMem / MB} lowMem=${memory.lowMemory}"
        )
        frames = 0
        late = 0
        worstNs = 0L
    }

    private const val MB = 1024L * 1024L
}
