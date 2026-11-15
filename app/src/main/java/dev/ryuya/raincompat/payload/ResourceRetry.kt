package dev.ryuya.raincompat.payload

/** Retry only a missing result. Exceptions remain exceptions and healthy calls execute once. */
internal object ResourceRetry {
    fun <T : Any> recover(result: T?, retry: () -> T?, outcome: (Boolean) -> Unit): T? {
        if (result != null) return result
        val recovered = retry()
        outcome(recovered != null)
        return recovered
    }
}
