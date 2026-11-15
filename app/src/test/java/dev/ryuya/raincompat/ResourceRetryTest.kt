package dev.ryuya.raincompat

import dev.ryuya.raincompat.payload.ResourceRetry
import org.junit.Assert.*
import org.junit.Test

class ResourceRetryTest {
    @Test
    fun healthyResourceIsNotReplacedOrRetried() {
        val resource = Any()
        assertSame(
            resource,
            ResourceRetry.recover(
                resource,
                { error("Unexpected retry") },
                { error("Unexpected report") },
            ),
        )
    }

    @Test
    fun missingResourceCanRecoverWithoutReplacingHealthyResources() {
        val resource = Any()
        var calls = 0
        var success: Boolean? = null
        assertSame(
            resource,
            ResourceRetry.recover(
                null,
                {
                    calls++
                    resource
                },
                { success = it },
            ),
        )
        assertEquals(1, calls)
        assertEquals(true, success)
    }

    @Test
    fun persistentFailureDoesNotLoopOrInventResources() {
        var calls = 0
        var success: Boolean? = null
        assertNull(
            ResourceRetry.recover<Any>(
                null,
                {
                    calls++
                    null
                },
                { success = it },
            ),
        )
        assertEquals(1, calls)
        assertEquals(false, success)
    }

    @Test
    fun originalExceptionIsNotSuppressed() {
        val exception = IllegalStateException("framework failure")
        val thrown =
            assertThrows(IllegalStateException::class.java) {
                ResourceRetry.recover<Any>(null, { throw exception }, { error("False success") })
            }
        assertSame(exception, thrown)
    }
}
