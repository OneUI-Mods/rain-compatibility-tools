package dev.ryuya.raincompat.payload.live

import dev.ryuya.raincompat.payload.RetryPolicyV1

internal class SignedRetryPolicy : RetryPolicyV1 {
    override val apiVersion = 1

    // The host has already verified the exact process, activity and absent resource result.
    override fun shouldRetry(): Boolean = true
}
