package dev.ryuya.raincompat

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle

/** Only Rain's UID can read its toggle or report a fixed diagnostic state. */
class ConfigProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = requireNotNull(context)
        // Root-only command integration records a deployment performed over ADB.
        // This endpoint never executes a command or reads any Rain data.
        if (method == "record_install") {
            if (Binder.getCallingUid() != 0) throw SecurityException("Root required")
            val sha = requireNotNull(extras?.getString("sha"))
            require(sha.matches(Regex("[0-9a-f]{64}")))
            val enabled = extras.getBoolean("enabled", false)
            app.getSharedPreferences("settings", 0)
                .edit()
                .putString("installed_sha", sha)
                .putBoolean("enabled", enabled)
                .putString("state", if (enabled) "waiting" else "disabled")
                .commit()
            return Bundle()
        }
        val packages = app.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty()
        if ("dev.raincord.app" !in packages) throw SecurityException("Rain required")
        val prefs = app.getSharedPreferences("settings", 0)
        return when (method) {
            "read" -> Bundle().apply { putBoolean("enabled", prefs.getBoolean("enabled", true)) }
            "report" -> {
                val state = extras?.getString("state")
                require(
                    state in
                        setOf(
                            "ready",
                            "disabled",
                            "unsupported",
                            "setup_failed",
                            "repaired",
                            "retry_failed",
                        ),
                )
                val edit =
                    prefs
                        .edit()
                        .putString("state", state)
                        .putLong("updated", System.currentTimeMillis())
                if (state == "repaired") edit.putInt("repairs", prefs.getInt("repairs", 0) + 1)
                if (state == "retry_failed") edit.putInt("failed", prefs.getInt("failed", 0) + 1)
                edit.apply()
                Bundle()
            }
            else -> throw IllegalArgumentException("Unknown operation")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()

    companion object {
        val URI: Uri = Uri.parse("content://dev.ryuya.raincompat.config")
    }
}
