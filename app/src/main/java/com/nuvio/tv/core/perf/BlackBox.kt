package com.nuvio.tv.core.perf

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Personal build: tiny persistent event log for the Philips TV.
 *
 * Logcat on the TV rolls over within hours, so the reason the app closed or images went missing
 * is usually gone by the time anyone looks. This keeps short one-line events in
 * /sdcard/Android/data/<pkg>/files/blackbox.log (readable over adb without root), capped at 256 KB
 * with one rotated copy. Writes happen on one background thread; callers only enqueue a string.
 * Events: app start (and whether the previous session ended without passing onStop), crashes,
 * trim-memory levels, image load failures (rate-limited), and anything else passed to [log].
 */
object BlackBox {
    private const val TAG = "BlackBox"
    private const val MAX_BYTES = 256 * 1024
    private const val SESSION_MARKER = "session.open"

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "BlackBox").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
    }
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var file: File? = null
    @Volatile private var markerFile: File? = null
    private val lastImageErrorAt = HashMap<String, Long>()

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        file = File(dir, "blackbox.log")
        markerFile = File(dir, SESSION_MARKER)
        val previousOpen = markerFile?.exists() == true
        log("app_start previous_session_unclean=$previousOpen ${memorySummary(context)}")
        installCrashHandler()
    }

    /** Foreground: the app is in use. Missing on the next start means it died while in use. */
    fun markForeground() {
        executor.execute { runCatching { markerFile?.writeText(System.currentTimeMillis().toString()) } }
    }

    /** onStop passed normally: a later kill in the background is ordinary memory management. */
    fun markBackground() {
        executor.execute { runCatching { markerFile?.delete() } }
    }

    fun log(message: String) {
        val line = "${timeFormat.format(Date())} $message\n"
        Log.i(TAG, message)
        val target = file ?: return
        executor.execute {
            runCatching {
                if (target.length() > MAX_BYTES) {
                    val rotated = File(target.parentFile, "blackbox.1.log")
                    rotated.delete()
                    target.renameTo(rotated)
                }
                target.appendText(line)
            }
        }
    }

    /** Image failures can come in bursts; one line per host and error type every 30 s. */
    fun imageError(host: String, error: Throwable?) {
        val kind = error?.javaClass?.simpleName ?: "unknown"
        val key = "$host|$kind"
        val now = SystemClock.elapsedRealtime()
        synchronized(lastImageErrorAt) {
            val last = lastImageErrorAt[key]
            if (last != null && now - last < 30_000) return
            lastImageErrorAt[key] = now
        }
        log("image_error host=$host error=$kind msg=${error?.message?.take(120)}")
    }

    fun memorySummary(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val rt = Runtime.getRuntime()
        val heapMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
        val nativeMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        return "heapMb=$heapMb nativeMb=$nativeMb availMb=${info.availMem / (1024 * 1024)} lowMem=${info.lowMemory}"
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Written synchronously: the process is about to die.
            runCatching {
                file?.appendText(
                    "${timeFormat.format(Date())} crash thread=${thread.name} " +
                        "${Log.getStackTraceString(error).take(4000)}\n"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
