package dev.ryuya.raincompat.payload.live

import android.content.res.Resources
import dev.ryuya.raincompat.payload.ResourceRecoveryV2
import dev.ryuya.raincompat.payload.ResourceRetry
import java.util.concurrent.Callable

internal class SignedResourceRecovery : ResourceRecoveryV2 {
    override val apiVersion = 2

    // The resident hook has verified that Rain received no Resources for this activity.
    override fun recover(retry: Callable<Resources?>): Resources? =
        ResourceRetry.recover(null, { retry.call() }, {})
}
