package dev.ryuya.raincompat

import android.content.Context
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Changes only the extracted LSPatch module cache; installed Rain APK and account data stay intact.
 */
internal class CacheInstaller(private val context: Context) {
    private val settings = context.getSharedPreferences("settings", 0)

    fun apply(enabled: Boolean) {
        check(Build.VERSION.SDK_INT == 37 && android.os.Process.myUid() < 100000) {
            "Supported only on Android 17 in the main profile"
        }
        val info = context.packageManager.getPackageInfo(TARGET, 0)
        check(info.longVersionCode == 344013L) {
            "Unsupported Rain version"
        }
        val source = File(requireNotNull(info.applicationInfo).sourceDir)
        check(sha256(source) == BASE_SHA) { "Rain APK differs from the supported build" }
        val work = File(context.filesDir, "payload").apply { mkdirs() }
        val original = File(work, "original-module.apk")
        ZipFile(source).use { apk ->
            val entry =
                requireNotNull(apk.getEntry("assets/lspatch/modules/dev.raincord.xposed.apk"))
            check(entry.crc == 3404327306L)
            apk.getInputStream(entry).use { input ->
                original.outputStream().use { input.copyTo(it) }
            }
        }
        check(sha256(original) == ORIGINAL_SHA) { "Embedded module checksum mismatch" }
        val targetUid = requireNotNull(info.applicationInfo).uid
        check(targetUid in 10000..19999) { "Main-profile Rain UID required" }
        val ownership = root("stat -c '%u:%g' ${quote(CACHE)}")
        check(
            ownership.matches(Regex("[0-9]+:[0-9]+")) &&
                ownership.substringBefore(':').toInt() == targetUid,
        ) {
            "Unexpected cache owner; left unchanged"
        }
        val previous = root("sha256sum ${quote(CACHE)}").substringBefore(' ')
        val ownPrevious = settings.getString("installed_sha", null)
        check(previous == ORIGINAL_SHA || previous == ownPrevious) {
            "Unknown cache contents; left unchanged"
        }
        val output = if (enabled) patch(original, work) else original
        val expected = sha256(output)
        // Atomically replace the extracted module without restarting Rain. LSPatch has already
        // loaded the old DEX into memory; the change takes effect at the next natural startup.
        root(
            """
            set -eu
            test "${'$'}(sha256sum ${quote(source.path)} | cut -d ' ' -f 1)" = ${quote(BASE_SHA)}
            test "${'$'}(sha256sum ${quote(CACHE)} | cut -d ' ' -f 1)" = ${quote(previous)}
            cp ${quote(output.path)} ${quote("$CACHE.raincompat-new")}
            chown $ownership ${quote("$CACHE.raincompat-new")}
            chmod 600 ${quote("$CACHE.raincompat-new")}
            restorecon ${quote("$CACHE.raincompat-new")}
            test "${'$'}(sha256sum ${quote("$CACHE.raincompat-new")} | cut -d ' ' -f 1)" = ${quote(expected)}
            mv -f ${quote("$CACHE.raincompat-new")} ${quote(CACHE)}
            restorecon ${quote(CACHE)}
        """
                .trimIndent(),
        )
        settings
            .edit()
            .putBoolean("enabled", enabled)
            .putString("installed_sha", expected)
            .putString("state", if (enabled) "waiting" else "disabled")
            .commit()
    }

    private fun patch(original: File, work: File): File {
        val output = File(work, "patched-module.apk")
        ZipFile(original).use { input ->
            check(input.getEntry("classes2.dex") == null) { "Unexpected module layout" }
            ZipOutputStream(output.outputStream()).use { zip ->
                input
                    .entries()
                    .asSequence()
                    .filterNot {
                        it.name == "assets/xposed_init" || it.name.startsWith("META-INF/")
                    }
                    .forEach { entry ->
                        zip.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) input.getInputStream(entry).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                zip.putNextEntry(ZipEntry("assets/xposed_init"))
                val entry = requireNotNull(input.getEntry("assets/xposed_init"))
                val names =
                    input.getInputStream(entry).bufferedReader().use { it.readText() }.trim()
                check(names == "io.github.revenge.xposed.Main") { "Unknown Rain entrypoint" }
                zip.write(
                    (names + "\ndev.ryuya.raincompat.payload.RainResourceHook\n").toByteArray(),
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("classes2.dex"))
                context.assets.open("classes.dex").use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output
    }

    fun diagnostics(): String =
        root("tail -n 30 /data/user/0/dev.raincord.app/files/raincompat/diagnostics.jsonl")

    private fun root(command: String): String {
        val resultFile = File(context.cacheDir, "root-result.txt")
        val process =
            ProcessBuilder("su", "-M", "-c", command)
                .redirectErrorStream(true)
                .redirectOutput(resultFile)
                .start()
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Root command timed out")
        }
        val output = resultFile.readText().trim()
        resultFile.delete()
        check(process.exitValue() == 0) { "Root operation failed: ${output.take(300)}" }
        return output
    }

    private fun sha256(file: File): String =
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    companion object {
        private const val TARGET = "dev.raincord.app"
        private const val CACHE =
            "/data/user/0/dev.raincord.app/cache/lspatch/dev.raincord.xposed/3404327306.apk"
        private const val BASE_SHA =
            "ad3f403589d4681f6efbf200b900022b4c438b31a27d88b4903387ee6af850f2"
        private const val ORIGINAL_SHA =
            "4532b8c4041d54888206ae9501f841003f60ec20acf06480774ffcfbf2295066"
    }
}
