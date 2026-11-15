package dev.ryuya.raincompat.payload

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import org.json.JSONObject

/** Bounded, event-driven diagnostics. No account data, messages, stack traces or polling. */
internal object Diagnostics {
    private var folder: File? = null

    @Synchronized
    fun attach(context: Context) {
        try {
            folder = File(context.filesDir, "raincompat").apply { mkdirs() }
            event("application_attached")
            val directory = requireNotNull(folder)
            val marker = File(directory, "last-exit-time")
            val since = if (marker.exists()) marker.readText().toLongOrNull() ?: 0 else 0
            val exits =
                context
                    .getSystemService(ActivityManager::class.java)
                    ?.getHistoricalProcessExitReasons(null, 0, 8)
                    .orEmpty()
            var newest = since
            for (exit in exits.asReversed()) {
                if (exit.timestamp <= since || exit.processName != "dev.raincord.app") continue
                write(
                    JSONObject()
                        .put("event", "previous_exit")
                        .put("time", exit.timestamp)
                        .put("reason", exit.reason)
                        .put("status", exit.status)
                        .put("pid", exit.pid),
                )
                newest = maxOf(newest, exit.timestamp)
            }
            if (newest > since) marker.writeText(newest.toString())
        } catch (error: Exception) {
            Log.w("RainCompat", "Diagnostic storage unavailable: ${error.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun event(name: String) {
        try {
            if (folder == null) return
            write(
                JSONObject()
                    .put("event", name)
                    .put("time", System.currentTimeMillis())
                    .put("pid", Process.myPid()),
            )
        } catch (error: Exception) {
            Log.w("RainCompat", "Diagnostic write unavailable: ${error.javaClass.simpleName}")
        }
    }

    private fun write(event: JSONObject) {
        val directory = folder ?: return
        val current = File(directory, "diagnostics.jsonl")
        if (current.length() >= 32768) {
            val previous = File(directory, "diagnostics.previous.jsonl")
            if (previous.exists()) check(previous.delete())
            check(current.renameTo(previous))
        }
        current.appendText(event.toString() + "\n")
    }
}
