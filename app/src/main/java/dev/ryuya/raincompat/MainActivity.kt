package dev.ryuya.raincompat

import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private val worker =
        java.util.concurrent.ThreadPoolExecutor(
            0,
            1,
            1,
            java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.LinkedBlockingQueue(),
        )

    private val settings
        get() = getSharedPreferences("settings", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<MaterialSwitch>(R.id.enabled_switch).apply {
            isChecked = settings.getBoolean("enabled", false)
            setOnClickListener {
                val requested = isChecked
                isEnabled = false
                worker.execute {
                    val error = runCatching {
                        CacheInstaller(this@MainActivity).apply(requested)
                    }
                        .exceptionOrNull()
                    runOnUiThread {
                        isEnabled = true
                        isChecked = settings.getBoolean("enabled", false)
                        if (error != null)
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle(R.string.operation_failed)
                                .setMessage(error.message)
                                .setPositiveButton(R.string.close, null)
                                .show()
                        else
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setMessage(R.string.applied)
                                .setPositiveButton(R.string.close, null)
                                .show()
                    }
                }
            }
        }
        findViewById<View>(R.id.diagnostics).setOnClickListener {
            worker.execute {
                val result = runCatching {
                    CacheInstaller(this)
                        .diagnostics()
                        .lineSequence()
                        .filter { it.isNotBlank() }
                        .map { line ->
                            val event = org.json.JSONObject(line)
                            val time =
                                DateFormat.getDateTimeInstance().format(Date(event.getLong("time")))
                            val label =
                                when (event.getString("event")) {
                                    "application_attached" -> getString(R.string.event_attached)
                                    "activity_creation_started" ->
                                        getString(R.string.event_activity_start)
                                    "activity_context_created" ->
                                        getString(R.string.event_activity_ready)
                                    "activity_creation_threw" ->
                                        getString(R.string.event_activity_failed)
                                    "repaired" -> getString(R.string.status_repaired)
                                    "retry_failed" -> getString(R.string.status_retry_failed)
                                    "previous_exit" ->
                                        getString(
                                            R.string.event_exit,
                                            event.optInt("reason"),
                                            event.optInt("status"),
                                        )
                                    else -> getString(R.string.event_unknown)
                                }
                            "$time\n$label"
                        }
                        .joinToString("\n\n")
                }
                runOnUiThread {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.status_title)
                        .setMessage(
                            result.getOrNull() ?: getString(R.string.diagnostics_unavailable),
                        )
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
        }
        findViewById<View>(R.id.licenses).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.licenses)
                .setItems(
                    arrayOf(
                        getString(R.string.app_license),
                        "Material Components · Apache 2.0",
                        "AndroidX · Apache 2.0",
                        "Kotlin · licenses",
                        "Xposed API (compile only) · Apache 2.0",
                    ),
                ) { _, index ->
                    showLicense(
                        when (index) {
                            0 -> "APP_LICENSE.txt"
                            3 -> "KOTLIN-2.2.10.txt"
                            else -> "Apache-2.0.txt"
                        },
                    )
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
        findViewById<TextView>(R.id.version).text =
            getString(R.string.version, BuildConfig.VERSION_NAME)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.state)
            .setText(
                if (settings.getBoolean("enabled", false)) R.string.config_on
                else R.string.config_off,
            )
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun showLicense(file: String) {
        val content = assets.open(file).bufferedReader().use { it.readText() }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.licenses)
            .setMessage(content)
            .setPositiveButton(R.string.close, null)
            .show()
    }
}
