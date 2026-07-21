package com.feedbackthread.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

public class FeedbackThreadIdentityTest {
    @Test
    public fun returnsTheProvidedExternalIdTrimmedWithoutFallingBack() {
        var anonymousCalls = 0
        val resolved = resolveVoterId("  user-42  ") {
            anonymousCalls++
            "anon-id"
        }

        assertEquals("user-42", resolved)
        assertEquals(0, anonymousCalls)
    }

    @Test
    public fun fallsBackToTheAnonymousIdWhenExternalIdIsNull() {
        assertEquals("anon-id", resolveVoterId(null) { "anon-id" })
    }

    @Test
    public fun fallsBackToTheAnonymousIdWhenExternalIdIsBlank() {
        assertEquals("anon-id", resolveVoterId("   ") { "anon-id" })
    }
}
