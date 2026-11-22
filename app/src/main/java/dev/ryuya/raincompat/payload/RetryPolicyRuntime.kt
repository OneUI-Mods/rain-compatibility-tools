package dev.ryuya.raincompat.payload

import android.content.Context
import android.content.res.Resources
import android.util.Log
import dev.ryuya.raincompat.BuildConfig
import dev.ryuya.raincompat.payload.live.SignedResourceRecovery
import java.util.concurrent.Callable

internal interface RetryPolicyV1 {
    val apiVersion: Int
    fun shouldRetry(): Boolean
}

internal interface ResourceRecoveryV2 {
    val apiVersion: Int
    fun recover(retry: Callable<Resources?>): Resources?
}

internal object RetryPolicyRuntime {
    private val bundled = SignedResourceRecovery()
    private val api = SignedLogic(
        BuildConfig.APPLICATION_ID,
        BuildConfig.VERSION_CODE.toLong(),
        "7fff3c5765ee1fb0a351c286ec190de50905feac997c9a991befe6fd6fb4c281",
        ResourceRecoveryV2::class.java,
        "dev.ryuya.raincompat.payload.live.SignedResourceRecovery",
        bundled,
        accepts = { it.apiVersion == 2 },
        report = { Log.i("RainCompat", it) },
    )

    fun start(context: Context) = api.start(context)

    fun recover(original: () -> Resources?, outcome: (Boolean) -> Unit): Resources? {
        val callerThread = Thread.currentThread()
        var open = true
        var attempted: Result<Resources?>? = null
        val retry = Callable {
            check(Thread.currentThread() === callerThread && open) {
                "The native retry is available only during the resource callback"
            }
            val result = attempted ?: runCatching { original() }.also { attempted = it }
            result.getOrThrow()
        }
        val candidate = api.current
        try {
            val recovered = try {
                val result = candidate.logic.recover(retry)
                check(result == null || result === attempted?.getOrNull()) {
                    "Recovery must return the native resource result"
                }
                result
            } catch (error: RuntimeException) {
                attempted?.exceptionOrNull()?.let { throw it }
                api.reject(candidate, error)
                bundled.recover(retry)
            } catch (error: LinkageError) {
                attempted?.exceptionOrNull()?.let { throw it }
                api.reject(candidate, error)
                bundled.recover(retry)
            }
            attempted?.getOrThrow()
            if (attempted != null) outcome(recovered != null)
            return recovered
        } finally {
            open = false
        }
    }
}
